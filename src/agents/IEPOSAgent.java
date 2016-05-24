/*
 * Copyright (C) 2015 Evangelos Pournaras
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package agents;

import agents.plan.AggregatePlan;
import agents.plan.CombinationalPlan;
import agents.plan.GlobalPlan;
import agents.fitnessFunction.IterativeFitnessFunction;
import agents.fitnessFunction.costFunction.CostFunction;
import agents.plan.Plan;
import agents.plan.PossiblePlan;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import messages.IEPOSDown;
import messages.IEPOSUp;
import org.joda.time.DateTime;
import agents.dataset.AgentDataset;
import java.util.Random;

/**
 *
 * @author Evangelos
 */
public class IEPOSAgent extends IterativeAgentTemplate<IEPOSUp, IEPOSDown> {
    private boolean outputMovie;

    private final int historySize;
    private final TreeMap<DateTime, AgentPlans> history = new TreeMap<>();

    private int numNodes;
    private int numNodesSubtree;
    private int layer;
    private double avgNumChildren;

    private final IterativeFitnessFunction fitnessFunctionPrototype;
    private IterativeFitnessFunction fitnessFunction;
    private IterativeFitnessFunction fitnessFunctionRoot;
    private final List<Double> measurements = new ArrayList<>();
    
    private Plan costSignal;
    private AgentPlans current = new AgentPlans();
    private AgentPlans previous = new AgentPlans();
    private AgentPlans prevAggregate = new AgentPlans();
    private AgentPlans historic;
    
    private Double rampUpRate;
    
    private List<Integer> selectedCombination = new ArrayList<>();
    private LocalSearch localSearch;

    public static class Factory extends AgentFactory {
        
        @Override
        public Agent create(int id, AgentDataset dataSource, String treeStamp, File outFolder, DateTime initialPhase, DateTime previousPhase, Plan costSignal, int historySize) {
            return new IEPOSAgent(id, dataSource, treeStamp, outFolder, (IterativeFitnessFunction) fitnessFunction, initialPhase, previousPhase, costSignal, historySize, numIterations, localSearch, outputMovie, getMeasures(), getLocalMeasures(), rampUpRate, inMemory);
        }
    
        @Override
        public String toString() {
            return "IEPOS";
        }
    }

    public IEPOSAgent(int id, AgentDataset dataSource, String treeStamp, File outFolder, IterativeFitnessFunction fitnessFunction, DateTime initialPhase, DateTime previousPhase, Plan costSignal, int historySize, int numIterations, LocalSearch localSearch, boolean outputMovie, List<CostFunction> measures, List<CostFunction> localMeasures, Double rampUpRate, boolean inMemory) {
        super(id, dataSource, treeStamp, outFolder, initialPhase, numIterations, measures, localMeasures, inMemory);
        this.fitnessFunctionPrototype = fitnessFunction;
        this.historySize = historySize;
        this.costSignal = costSignal;
        this.localSearch = localSearch==null?null:localSearch.clone();
        this.outputMovie = outputMovie;
        this.rampUpRate = rampUpRate;
    }

    @Override
    void initPhase() {
        if (this.history.size() > this.historySize) {
            this.history.remove(this.history.firstKey());
        }
        prevAggregate.reset();
        previous.reset();
        
        if(previousPhase != null) {
            this.historic = history.get(previousPhase);
        } else {
            this.historic = null;
        }
        numNodes = -1;
        fitnessFunction = fitnessFunctionPrototype.clone();
        if(isRoot()) {
            fitnessFunctionRoot = fitnessFunctionPrototype.clone();
        }
    }
    
    @Override
    void initIteration() {
        current = new AgentPlans();
        current.globalPlan = new GlobalPlan(this);
        current.aggregatePlan = new AggregatePlan(this);
        current.selectedPlan = new PossiblePlan(this);
        current.selectedCombinationalPlan = new CombinationalPlan(this);
        numNodesSubtree = 1;
        avgNumChildren = children.size();
        layer = 0;
    }

    @Override
    public IEPOSUp up(List<IEPOSUp> msgs) {
        if(!msgs.isEmpty()) {
            Plan childAggregatePlan = new AggregatePlan(this);
            List<Plan> combinationalPlans = new ArrayList<>();
            List<List<Integer>> combinationalSelections = new ArrayList<>();
            
            if(localSearch != null) {
                List<Plan> childAggregatePlans = new ArrayList<>();
                for(IEPOSUp msg : msgs) {
                    childAggregatePlans.add(msg.aggregatePlan);
                }
                childAggregatePlan = localSearch.calcAggregate(this, childAggregatePlans, previous.globalPlan);
            } else {
                for(IEPOSUp msg : msgs) {
                    childAggregatePlan.add(msg.aggregatePlan);
                }
            }
            
            // init combinations
            int numCombinations = 1;
            for (IEPOSUp msg : msgs) {
                numNodesSubtree += msg.numNodes;
                numCombinations *= msg.possiblePlans.size();
            }

            for (int i = 0; i < numCombinations; i++) {
                combinationalPlans.add(new CombinationalPlan(this));
                combinationalSelections.add(new ArrayList<>());
            }

            // calc all possible combinations
            int factor = 1;
            for (IEPOSUp msg : msgs) {
                List<Plan> childPlans = msg.possiblePlans;
                int numPlans = childPlans.size();
                for (int i = 0; i < numCombinations; i++) {
                    int planIdx = (i / factor) % numPlans;
                    Plan combinationalPlan = combinationalPlans.get(i);
                    combinationalPlan.add(childPlans.get(planIdx));
                    combinationalPlan.setDiscomfort(combinationalPlan.getDiscomfort() + childPlans.get(planIdx).getDiscomfort());
                    combinationalSelections.get(i).add(planIdx);
                }
                factor *= numPlans;
            }

            // select best combination
            List<Plan> subSelectablePlans = combinationalPlans;
            List<List<Integer>> subCombinationalSelections = combinationalSelections;
            if(rampUpRate != null && !isRoot()) {
                Random r = new Random(getPeer().getIndexNumber());
                int numPlans = (int) Math.floor(iteration * rampUpRate + 2 + r.nextDouble()*2-1);
                subSelectablePlans = combinationalPlans.subList(0, Math.min(numPlans,possiblePlans.size()));
                subCombinationalSelections = subCombinationalSelections.subList(0, Math.min(numPlans,combinationalPlans.size()));;
                /*List<Plan> plans = new ArrayList<>(combinationalPlans);
                subSelectablePlans = new ArrayList<>();
                subCombinationalSelections = new ArrayList<>();
                for(int i = 0; i < numPlans && !plans.isEmpty(); i++) {
                    int idx = r.nextInt(plans.size());
                    subSelectablePlans.add(plans.get(idx));
                    subCombinationalSelections.add(combinationalSelections.get(idx));
                    plans.remove(idx);
                }*/
            }
            int selectedCombination = fitnessFunction.select(this, childAggregatePlan, subSelectablePlans, costSignal, prevAggregate, numNodes, numNodesSubtree, layer, avgNumChildren, iteration);
            this.selectedCombination = subCombinationalSelections.get(selectedCombination);
            
            current.selectedCombinationalPlan = subSelectablePlans.get(selectedCombination);
            current.aggregatePlan.set(childAggregatePlan);
            current.aggregatePlan.add(current.selectedCombinationalPlan);
        }
        
        IEPOSUp msg = new IEPOSUp();
        msg.possiblePlans = possiblePlans;
        msg.aggregatePlan = current.aggregatePlan;
        msg.numNodes = numNodesSubtree;
        return msg;
    }

    @Override
    public IEPOSDown atRoot(IEPOSUp rootMsg) {
        int selected = fitnessFunctionRoot.select(this, current.aggregatePlan, possiblePlans, costSignal, prevAggregate, numNodes, numNodesSubtree, layer, avgNumChildren, iteration);
        current.selectedPlan = possiblePlans.get(selected);
        measureLocal(current.selectedPlan, costSignal, selected, possiblePlans.size());
        current.globalPlan.set(current.aggregatePlan);
        current.globalPlan.add(current.selectedPlan);

        this.history.put(this.currentPhase, current);

        // Log + output
        //robustness = fitnessFunction.getRobustness(current.globalPlan, costSignal, historic);
        //log.log(measurementEpoch, iteration, robustness);
        //getPeer().getMeasurementLogger().log(measurementEpoch, iteration, robustness);
        //System.out.println(planSize + "," + currentPhase.toString("yyyy-MM-dd") + "," + robustness + ": " + current.globalPlan);
        if(outputMovie) {
            if(iteration == 0) {
                System.out.println("C(1:"+costSignal.getNumberOfStates()+","+(iteration+1)+")="+costSignal+";");
            }
            System.out.println("D(1:"+costSignal.getNumberOfStates()+","+(iteration+1)+")="+current.globalPlan+";");
            
            Plan c = costSignal.clone();
            if(iteration > 0) {
                c.multiply(1+iteration);
                c.add(prevAggregate.globalPlan);
            }
            System.out.println("T(1:"+costSignal.getNumberOfStates()+","+(iteration+1)+")="+c+";");
        } else {
            if(iteration%10 == 9) {
                System.out.print("%");
            }
            if(iteration%100 == 99) {
                System.out.print(" ");
            }
            if(iteration+1 == numIterations) {
                System.out.println("");
            }
        }
        
        IEPOSDown msg = new IEPOSDown(current.globalPlan, numNodesSubtree, 0, 0, selected);
        return msg;
    }

    @Override
    public List<IEPOSDown> down(IEPOSDown parent) {
        current.globalPlan.set(parent.globalPlan);
        if(parent.discard) {
            current.aggregatePlan = previous.aggregatePlan;
            current.selectedPlan = previous.selectedPlan;
            current.selectedCombinationalPlan = previous.selectedCombinationalPlan;
        } else {
            current.selectedPlan.set(possiblePlans.get(parent.selected));
        }
        measureLocal(current.selectedPlan, costSignal, parent.selected, possiblePlans.size());
        
        if(isRoot()) {
            measureGlobal(costSignal, costSignal);
        }
        
        numNodes = parent.numNodes;
        layer = parent.hops;
        avgNumChildren = parent.sumChildren/Math.max(0.1,(double)parent.hops);

        fitnessFunction.updatePrevious(prevAggregate, current, costSignal, iteration);
        if(isRoot()) {
            fitnessFunctionRoot.updatePrevious(null, current, costSignal, iteration);
        }
        previous = current;
        
        List<IEPOSDown> msgs = new ArrayList<>();
        for(int i=0;i<selectedCombination.size(); i++) {
            int selected = selectedCombination.get(i);
            IEPOSDown msg = new IEPOSDown(parent.globalPlan, parent.numNodes, parent.hops+1, parent.sumChildren+children.size(), selected);
            if(localSearch != null) {
                msg.discard = parent.discard || !localSearch.getSelected().get(i);
            }
            msgs.add(msg);
        }
        return msgs;
    }
}

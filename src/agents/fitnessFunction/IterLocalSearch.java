/*
 * Copyright (C) 2016 Evangelos Pournaras
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
package agents.fitnessFunction;

import agents.fitnessFunction.iterative.PlanCombinator;
import agents.Agent;
import agents.plan.AggregatePlan;
import agents.plan.Plan;
import agents.AgentPlans;
import agents.fitnessFunction.iterative.MostRecentCombinator;
import agents.fitnessFunction.iterative.NoOpCombinator;
import agents.plan.GlobalPlan;
import java.util.List;

/**
 * minimize variance (submodular/convex compared to std deviation)
 * weight B according to optimum without aggregate and equal Bi
 * @author Peter
 */
public class IterLocalSearch extends IterativeFitnessFunction {
    public IterLocalSearch() {
        super(new MostRecentCombinator(), new MostRecentCombinator(), new MostRecentCombinator(), new MostRecentCombinator());
    }

    @Override
    public double getRobustness(Plan plan, Plan costSignal, AgentPlans historic) {
        return Math.sqrt(plan.variance());
    }

    @Override
    public int select(Agent agent, Plan aggregatePlan, List<Plan> combinationalPlans, Plan pattern) {
        double minVariance = Double.MAX_VALUE;
        int selected = -1;
        int numOpt = 0;

        for (int i = 0; i < combinationalPlans.size(); i++) {
            Plan combinationalPlan = combinationalPlans.get(i);
            Plan testAggregatePlan = new AggregatePlan(agent);
            testAggregatePlan.add(aggregatePlan);
            testAggregatePlan.add(combinationalPlan);

            double variance = testAggregatePlan.variance();
            if (variance < minVariance) {
                minVariance = variance;
                selected = i;
                numOpt = 1;
            }/* else if(variance == minVariance) {
                numOpt++;
                if(Math.random()<=1.0/numOpt) {
                    selected = i;
                }
            }*/
        }

        return selected;
    }

    public int select(Agent agent, Plan childAggregatePlan, List<Plan> combinationalPlans, Plan pattern, AgentPlans historic, AgentPlans previous) {
        if(previous.globalPlan == null) {
            return select(agent, childAggregatePlan, combinationalPlans, pattern);
        } else {
            Plan g = new GlobalPlan(agent);
            g.set(previous.globalPlan);
            g.subtract(previous.aggregatePlan);
            g.add(childAggregatePlan);
            return select(agent, g, combinationalPlans, pattern);
        }
    }

    @Override
    public String toString() {
        return "IterLocalSearch";
    }
}

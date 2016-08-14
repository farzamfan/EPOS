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
package agents;

import agents.fitnessFunction.costFunction.CostFunction;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import messages.DownMessage;
import messages.UpMessage;
import org.joda.time.DateTime;
import protopeer.Finger;
import protopeer.network.Message;
import protopeer.time.Timer;
import protopeer.time.TimerListener;
import protopeer.util.quantities.Time;
import agents.dataset.AgentDataset;
import experiments.log.AgentLogger;
import experiments.log.MovieLogger;
import agents.plan.Plan;
import protopeer.measurement.MeasurementLog;

/**
 *
 * @author Peter
 */
public abstract class IterativeAgentTemplate<UP extends UpMessage, DOWN extends DownMessage> extends Agent {

    int numIterations;
    int iteration;

    private final Map<Finger, UP> messageBuffer = new HashMap<>();
    

    public IterativeAgentTemplate(int id, AgentDataset dataSource, String treeStamp, File outFolder, DateTime initialPhase, int numIterations, List<CostFunction> measure, List<CostFunction> localMeasure, List<AgentLogger> loggers, boolean inMemory) {
        super(id, dataSource, treeStamp, outFolder, initialPhase, measure, localMeasure, loggers, inMemory);
        this.numIterations = numIterations;
        this.iteration = numIterations;
    }
    
    
    public int getIteration() {
        return iteration;
    }
    
    public int getNumIterations() {
        return numIterations;
    }
    
    @Override
    void runActiveState() {
        if (iteration < numIterations-1) {
            Timer loadAgentTimer = getPeer().getClock().createNewTimer();
            loadAgentTimer.addTimerListener(new TimerListener() {
                public void timerExpired(Timer timer) {
                    runIteration();
                    runActiveState();
                }
            });
            loadAgentTimer.schedule(Time.inMilliseconds(1000));
        } else {
            super.runActiveState();
        }
    }

    @Override
    final void runPhase() {
        iteration = -1;
        possiblePlans.clear();
        
        initPhase();
        runIteration();
    }
    
    private final void runIteration() {
        iteration++;

        if (iteration < numIterations) {
            initIteration();
            if (isLeaf()) {
                goUp();
            }
        }
    }

    @Override
    public void handleIncomingMessage(Message message) {
        if (message instanceof UpMessage) {
            UP msg = (UP) message;
            messageBuffer.put(msg.child, msg);
            setCumComputation(Math.max(getCumComputations(), msg.cumComp));
            setCumTransmitted(Math.max(getCumTransmitted(), msg.cumTrans));
            if (children.size() <= messageBuffer.size()) {
                goUp();
            }
        } else if (message instanceof DownMessage) {
            DOWN msg = (DOWN) message;
            setCumComputation(Math.max(getCumComputations(), msg.cumComp));
            setCumTransmitted(Math.max(getCumTransmitted(), msg.cumTrans));
            goDown((DOWN) message);
        }
    }

    private void goUp() {
        if (iteration == 0) {
            readPlans();
        }
        
        List<UP> orderedMsgs = new ArrayList<>();
        for(Finger child : children) {
            orderedMsgs.add(messageBuffer.get(child));
        }
        messageBuffer.clear();

        UP msg = up(orderedMsgs);
        
        fixComputations();
        fixTransmitted();
        msg.cumComp = getCumComputations();
        msg.cumTrans = getCumTransmitted();

        msg.child = getPeer().getFinger();
        if (isRoot()) {
            goDown(atRoot(msg));
        } else {
            getPeer().sendMessage(parent.getNetworkAddress(), msg);
        }
    }

    private void goDown(DOWN parentMsg) {
        List<DOWN> msgs = down(parentMsg);
        fixComputations();
        fixTransmitted();
        for (int i = 0; i < msgs.size(); i++) {
            msgs.get(i).cumComp = getCumComputations();
            msgs.get(i).cumTrans = getCumTransmitted();
            getPeer().sendMessage(children.get(i).getNetworkAddress(), msgs.get(i));
        }
        //runIteration();
    }

    abstract void initPhase();

    abstract void initIteration();

    abstract UP up(List<UP> children);

    abstract DOWN atRoot(UP rootMsg);

    abstract List<DOWN> down(DOWN parent);
}

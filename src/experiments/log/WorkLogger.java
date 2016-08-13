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
package experiments.log;

import agents.Agent;
import agents.plan.Plan;
import protopeer.measurement.Aggregate;
import protopeer.measurement.MeasurementLog;

/**
 *
 * @author Peter
 */
public class WorkLogger extends AgentLogger {

    @Override
    public void init(int agentId) {
    }

    @Override
    public void initRoot(Plan costSignal) {
    }

    @Override
    public void log(MeasurementLog log, int epoch, Agent agent) {
        Token token = new Token(agent.experimentId, agent.getIteration());
        log.log(epoch, "numComputations", token, agent.getNumComputations());
        log.log(epoch, "numTransmitted", token, agent.getNumTransmitted());
    }

    @Override
    public void logRoot(MeasurementLog log, int epoch, Agent agent, Plan global) {
    }

    @Override
    public void print(MeasurementLog log) {
        MeasurementLog log2 = new MeasurementLog();
        int num = 0;
        for (Object tokenObj : log.getTagsOfType(Token.class)) {
            Token token = (Token) tokenObj;
            Aggregate comp = log.getAggregate("numComputations", tokenObj);
            Aggregate trans = log.getAggregate("numTransmitted", tokenObj);
            log2.log(0, "avgComp", token.iteration, comp.getAverage());
            log2.log(0, "maxComp", token.iteration, comp.getMax());
            log2.log(0, "avgTrans", token.iteration, trans.getAverage());
            log2.log(0, "maxTrans", token.iteration, trans.getMax());
            num = Math.max(num, token.iteration);
        }
        internalPrint(log2, "avgComp", num+1);
        internalPrint(log2, "maxComp", num+1);
        internalPrint(log2, "avgTrans", num+1);
        internalPrint(log2, "maxTrans", num+1);
    }
    
    private void internalPrint(MeasurementLog log, String tag, int numIter) {
        System.out.print(tag + "=new double[]{");
        for (int i = 0; i < numIter; i++) {
            System.out.print((i>0?",":"")+log.getAggregate(tag,i).getAverage());
        }
        System.out.println("};");
    }

    private class Token {

        int run;
        int iteration;

        public Token(int run, int iteration) {
            this.run = run;
            this.iteration = iteration;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 61 * hash + this.run;
            hash = 61 * hash + this.iteration;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Token other = (Token) obj;
            if (this.run != other.run) {
                return false;
            }
            if (this.iteration != other.iteration) {
                return false;
            }
            return true;
        }

    }
}

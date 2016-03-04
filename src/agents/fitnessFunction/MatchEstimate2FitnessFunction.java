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

import agents.EPOSAgent;
import agents.HistoricPlans;
import agents.energyPlan.AggregatePlan;
import agents.energyPlan.GlobalPlan;
import agents.energyPlan.Plan;
import java.util.List;

/**
 *
 * @author Peter
 */
public class MatchEstimate2FitnessFunction implements FitnessFunction {

    @Override
    public double getRobustness(Plan globalPlan, Plan pattern, HistoricPlans historic) {
        return globalPlan.rootMeanSquareError(pattern);
    }

    @Override
    public Plan select(EPOSAgent agent, Plan aggregatePlan, List<Plan> combinationalPlans, Plan pattern, HistoricPlans historic) {
        double minRootMeanSquaredError = Double.MAX_VALUE;
        Plan selected = null;

        for (Plan combinationalPlan : combinationalPlans) {
            Plan testAggregatePlan = new AggregatePlan(agent);
            testAggregatePlan.add(aggregatePlan);
            testAggregatePlan.add(combinationalPlan);

            Plan normalizedPatternPlan = new GlobalPlan(agent);
            normalizedPatternPlan.add(pattern);
            normalizedPatternPlan.reverse();
            normalizedPatternPlan.subtract(normalizedPatternPlan.avg());
            normalizedPatternPlan.multiply(testAggregatePlan.stdDeviation() / normalizedPatternPlan.stdDeviation());
            normalizedPatternPlan.add(testAggregatePlan.avg());

            double rootMeanSquaredError = normalizedPatternPlan.rootMeanSquareError(testAggregatePlan);
            if (rootMeanSquaredError < minRootMeanSquaredError) {
                minRootMeanSquaredError = rootMeanSquaredError;
                selected = combinationalPlan;
            }
        }

        return selected;
    }
}

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
package agents.fitnessFunction.costFunction;

import agents.plan.Plan;

/**
 *
 * @author Peter
 */
public class DirectionCostFunction extends IterativeCostFunction {

    @Override
    public double calcCost(Plan plan, Plan costSignal, Plan iterationCost) {
        if(iterationCost == null) {
            return costSignal.dot(plan);
        }
        return costSignal.dot(plan) + iterationCost.dot(plan);
    }

    @Override
    public Plan calcGradient(Plan plan, Plan costSignal) {
        return costSignal;
    }

    @Override
    public String toString() {
        return "Cost";
    }

    @Override
    public String getMetric() {
        return "cost";
    }
}

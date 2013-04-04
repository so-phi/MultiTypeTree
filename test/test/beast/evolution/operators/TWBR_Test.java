/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package test.beast.evolution.operators;

import beast.core.MCMC;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.likelihood.StructuredCoalescentLikelihood;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.operators.TypedWilsonBaldingRandom;
import beast.evolution.tree.MultiTypeTreeFromNewick;
import beast.util.Randomizer;
import beast.util.unittesting.MultiTypeTreeStatLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class TWBR_Test {
 
    @Test
    public void test1() throws Exception {
        System.out.println("TWBR_test 1");
        
        // Fix seed.
        Randomizer.setSeed(53);
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "((1[deme='0']:1,2[deme='0']:1)[deme='0']:1,"
                + "3[deme='0']:2)[deme='0']:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "newick", newickStr,
                "typeLabel", "deme",
                "nTypes", 2);
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("0.1 0.1");
        RealParameter popSizes = new RealParameter("7.0 7.0");
        MigrationModel migModel = new MigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
        
        // Assemble distribution:
        StructuredCoalescentLikelihood distribution =
                new StructuredCoalescentLikelihood();
        distribution.initByName(
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        // Set up state:
        State state = new State();
        state.initByName("stateNode", mtTree);
        
        // Set up operator:
        TypedWilsonBaldingRandom operator = new TypedWilsonBaldingRandom();
        operator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "mu", 0.2,
                "alpha", 0.2);
        
        // Set up stat analysis logger:
        MultiTypeTreeStatLogger logger = new MultiTypeTreeStatLogger();
        logger.initByName(
                "multiTypeTree", mtTree,
                "burninFrac", 0.1,
                "logEvery", 1000);
        
        // Set up MCMC:
        MCMC mcmc = new MCMC();
        mcmc.initByName(
                "chainLength", "10000000",
                "state", state,
                "distribution", distribution,
                "operator", operator,
                "logger", logger);
        
        // Run MCMC:
        mcmc.run();
        
        System.out.format("height mean = %s\n", logger.getHeightMean());
        System.out.format("height var = %s\n", logger.getHeightVar());
        System.out.format("height ESS = %s\n", logger.getHeightESS());
        
        // Compare analysis results with truth:        
        boolean withinTol = (logger.getHeightESS()>1000)
                && (Math.abs(logger.getHeightMean()-19)<0.5)
                && (Math.abs(logger.getHeightVar()-300)<50);
        
        Assert.assertTrue(withinTol);
    }
    
    @Test
    public void test2() throws Exception {
        System.out.println("TWBR_test 2");
        
        // Fix seed.
        Randomizer.setSeed(42);
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "((1[deme='1']:1,2[deme='0']:1)[deme='0']:1,"
                + "3[deme='0']:2)[deme='0']:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "newick", newickStr,
                "typeLabel", "deme",
                "nTypes", 2);
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("0.1 0.1");
        RealParameter popSizes = new RealParameter("7.0 7.0");
        MigrationModel migModel = new MigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
        
        // Assemble distribution:
        StructuredCoalescentLikelihood distribution =
                new StructuredCoalescentLikelihood();
        distribution.initByName(
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        // Set up state:
        State state = new State();
        state.initByName("stateNode", mtTree);
        
        // Set up operator:
        TypedWilsonBaldingRandom operator = new TypedWilsonBaldingRandom();
        operator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "mu", 0.2,
                "alpha", 0.2);
        
        // Set up stat analysis logger:
        MultiTypeTreeStatLogger logger = new MultiTypeTreeStatLogger();
        logger.initByName(
                "multiTypeTree", mtTree,
                "burninFrac", 0.1,
                "logEvery", 1000);
        
        // Set up MCMC:
        MCMC mcmc = new MCMC();
        mcmc.initByName(
                "chainLength", "10000000",
                "state", state,
                "distribution", distribution,
                "operator", operator,
                "logger", logger);
        
        // Run MCMC:
        mcmc.run();
        
        System.out.format("height mean = %s\n", logger.getHeightMean());
        System.out.format("height var = %s\n", logger.getHeightVar());
        System.out.format("height ESS = %s\n", logger.getHeightESS());
        
        // Compare analysis results with truth:        
        boolean withinTol = (logger.getHeightESS()>1000)
                && (Math.abs(logger.getHeightMean()-23)<0.3)
                && (Math.abs(logger.getHeightVar()-300)<30);
        
        Assert.assertTrue(withinTol);
    }
}

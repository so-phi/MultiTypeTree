/*
 * Copyright (C) 2012 Tim Vaughan
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
package beast.evolution.migrationmodel;

import beast.core.CalculationNode;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.core.parameter.RealParameter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;
import org.jblas.Eigen;
import org.jblas.MatrixFunctions;

/**
 * Basic plugin describing a simple Markovian migration model, for use by
 * ColouredTree operators and likelihoods. Note that this class and package are
 * just stubs. We expect to have something similar to the SubstitutionModel
 * class/interface eventually.
 * 
 * Note that the transition rate matrices exposed for the uniformization
 * recolouring operators are symmetrized variants of the actual rate
 * matrices, to allow for easier diagonalization.
 *
 * @author Tim Vaughan
 */
@Description("Basic plugin describing a simple Markovian migration model.")
public class MigrationModel extends CalculationNode implements Loggable {

    public Input<RealParameter> rateMatrixInput = new Input<RealParameter>(
            "rateMatrix",
            "Migration rate matrix",
            Validate.REQUIRED);
    public Input<RealParameter> popSizesInput = new Input<RealParameter>(
            "popSizes",
            "Deme population sizes.",
            Validate.REQUIRED);
    
    public Input<Double> uniformInitialRateInput = new Input<Double>(
            "uniformInitialRate",
            "Specify uniform rate with which to initialise matrix.  "
            + "Overrides previous dimension and value of matrix.");
    
    private RealParameter rateMatrix, popSizes;
    private double totalPopSize;
    private double mu;
    private int nTypes;
    private DoubleMatrix Q, R;
    private List<DoubleMatrix> RpowN;
    
    private boolean rateMatrixIsSquare;
    
    // Flag to indicate whether EV decompositions need updating.
    private boolean dirty;

    public MigrationModel() { }

    @Override
    public void initAndValidate() throws Exception {
        
        popSizes = popSizesInput.get();
        nTypes = popSizes.getDimension();
        rateMatrix = rateMatrixInput.get();
        
        if (uniformInitialRateInput.get() != null) {
            
            double rate = uniformInitialRateInput.get();
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<nTypes; i++) {
                for (int j=0; j<nTypes; j++) {
                    if (i==j)
                        continue;
                    
                    sb.append(String.valueOf(rate)).append(" ");
                }
            }
            rateMatrixInput.get().initByName("value", sb.toString());
        }
        
        if (rateMatrix.getDimension() == nTypes*nTypes)
            rateMatrixIsSquare = true;
        else {
            if (rateMatrix.getDimension() != nTypes*(nTypes-1)) {
                throw new IllegalArgumentException("Migration matrix has"
                        + "incorrect number of elements for given deme count.");
            } else
                rateMatrixIsSquare = false;
        }
        
        // Initialise caching array for powers of uniformized
        // transition matrix:
        RpowN = new ArrayList<DoubleMatrix>();
        
        dirty = true;
        updateMatrices();
    }

    /**
     * Ensure all local fields including matrices and eigenvalue decomposition
     * objects are consistent with current values held by inputs.
     */
    public void updateMatrices()  {
        
        if (!dirty)
            return;

        popSizes = popSizesInput.get();
        rateMatrix = rateMatrixInput.get();

        totalPopSize = 0.0;
        for (int i = 0; i < popSizes.getDimension(); i++)
            totalPopSize += popSizes.getArrayValue(i);

        mu = 0.0;
        Q = new DoubleMatrix(nTypes, nTypes);

        // Set up backward transition rate matrix:
        for (int i = 0; i < nTypes; i++) {
            Q.put(i,i, 0.0);
            for (int j = 0; j < nTypes; j++)
                if (i != j) {
                    Q.put(j, i, getRate(j, i));
                    Q.put(i, i, Q.get(i, i) - Q.get(j, i));
                }

            if (-Q.get(i, i) > mu)
                mu = -Q.get(i, i);
        }

        // Set up uniformised backward transition rate matrix:
        R = new DoubleMatrix(nTypes, nTypes);
        for (int i = 0; i < nTypes; i++)
            for (int j = 0; j < nTypes; j++) {
                R.put(j, i, Q.get(j, i) / mu);
                if (j == i)
                    R.put(j, i, R.get(j, i) + 1.0);
            }
        
        // Clear cached powers of R:
        RpowN.clear();
        RpowN.add(DoubleMatrix.eye(nTypes));

        dirty = false;
    }

    /**
     * @return number of demes in the migration model.
     */
    public int getNDemes() {
        return nTypes;
    }

    /**
     * Obtain element of rate matrix for migration model.
     *
     * @return Rate matrix element.
     */
    public double getRate(int i, int j) {
        if (i==j)
            return 0;
        
        if (rateMatrixIsSquare) {
            return rateMatrix.getValue(i*nTypes+j);            
        } else {
            if (j>i)
                j -= 1;
            return rateMatrix.getValue(i*(nTypes-1)+j);            
        }
    }
    
    /**
     * Set element of rate matrix for migration model.
     * This method should only be called by operators.
     * @param i
     * @param j
     * @param rate 
     */
    public void setRate(int i, int j, double rate) {
        if (i==j)
            return;
        
        if (rateMatrixIsSquare) {
            rateMatrix.setValue(i*nTypes+j, rate);
        } else {
            if (j>i)
                j -= 1;
            rateMatrix.setValue(i*(nTypes-1)+j, rate);
        }
        
        // Model is now dirty.
        dirty = true;
    }

    /**
     * Obtain effective population size of particular type/deme.
     *
     * @param i deme index
     * @return Effective population size.
     */
    public double getPopSize(int i) {
        return popSizes.getArrayValue(i);
    }
    
    /**
     * Set effective population size of particular type/deme.
     * 
     * @param i deme index
     * @param newSize 
     */
    public void setPopSize(int i, double newSize) {
        popSizes.setValue(i, newSize);
        dirty = true;
    }

    /**
     * Obtain total effective population size across all demes.
     *
     * @return Effective population size.
     */
    public double getTotalPopSize() {
        updateMatrices();
        return totalPopSize;
    }

    public double getMu() {
        updateMatrices();
        return mu;
    }
    
    public DoubleMatrix getR() {
        updateMatrices();
        return R;
    }
    
    public DoubleMatrix getQ() {
        updateMatrices();
        return Q;
    }
    
    public DoubleMatrix getRpowN(int n) {
        updateMatrices();
        
        if (n>=RpowN.size()) {
            int startN = RpowN.size();
            for (int i=startN; i<=n; i++) {
                RpowN.add(RpowN.get(i-1).mmul(R));
            }
        }
        
        return RpowN.get(n);
    }

    /**
     * CalculationNode implementations *
     */
    @Override
    protected boolean requiresRecalculation() {
        // we only get here if something is dirty
        dirty = true;
        return true;
    }

    @Override
    protected void restore() {
        dirty = true;
        super.restore();
    }

    /*
     * Methods implementing loggable interface
     */
    
    @Override
    public void init(PrintStream out) throws Exception {
        
        String outName;
        if (getID() == null || getID().matches("\\s*"))
            outName = "migModel";
        else
            outName = getID();
        
        for (int i=0; i<nTypes; i++) {
            out.print(outName + ".popSize_" + i + "\t");
        }

        for (int i=0; i<nTypes; i++) {
            for (int j=0; j<nTypes; j++) {
                if (i==j)
                    continue;
                out.format("%s.rateMatrixBackward_%d_%d\t", outName, i, j);
            }
        }
        
        for (int i=0; i<nTypes; i++) {
            for (int j=0; j<nTypes; j++) {
                if (i==j)
                    continue;
                out.format("%s.rateMatrixForward_%d_%d\t", outName, i, j);
            }
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {
                
        for (int i=0; i<nTypes; i++) {
            out.print(getPopSize(i) + "\t");
        }

        for (int i=0; i<nTypes; i++) {
            for (int j=0; j<nTypes; j++) {
                if (i==j)
                    continue;
                out.format("%g\t", getRate(i, j));
            }
        }
        
        for (int i=0; i<nTypes; i++) {
            for (int j=0; j<nTypes; j++) {
                if (i==j)
                    continue;
                out.format("%g\t", getRate(j, i)*getPopSize(j)/getPopSize(i));
            }
        }
    }

    @Override
    public void close(PrintStream out) {
    }
    
    /**
     *
     * @param args
     */
    public static void main (String [] args) {
        
        int n=10;
        DoubleMatrix Q = new DoubleMatrix(n, n);
        for (int i=0; i<n; i++) {
            for (int j=0; j<n; j++) {
                Q.put(i, j, i*n+j);
            }
        }
        MatrixFunctions.expm(Q.mul(0.001)).print();
        Q.print();
        
    }
}

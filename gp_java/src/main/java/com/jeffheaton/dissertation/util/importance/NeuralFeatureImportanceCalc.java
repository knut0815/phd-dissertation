package com.jeffheaton.dissertation.util.importance;

import org.encog.EncogError;
import org.encog.ml.data.MLDataSet;
import org.encog.neural.networks.BasicNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Jeff on 3/31/2016.
 */
public class NeuralFeatureImportanceCalc extends AbstractFeatureImportance {

    public void calculateFeatureImportance() {

    }

    @Override
    public String toString() {
        return getFeatures().toString();
    }

    @Override
    public void performRanking() {
        // Reset rankings
        for (FeatureRank rank : getFeatures()) {
            rank.setImportancePercent(0);
            rank.setTotalWeight(0);
        }

        if( ! (getModel() instanceof BasicNetwork)  ) {
            throw new EncogError("This algorithm only works for classes of type BasicNetwork");
        }

        BasicNetwork network = (BasicNetwork)getModel();

        // Sum weights for each input neuron
        for (int inputNueron = 0; inputNueron < network.getInputCount(); inputNueron++) {
            FeatureRank ranking = getFeatures().get(inputNueron);
            for (int nextNeuron = 0; nextNeuron < network.getLayerNeuronCount(1); nextNeuron++) {
                double i_h = network.getWeight(0, inputNueron, nextNeuron);
                double h_o = network.getWeight(1, nextNeuron, 0);
                ranking.addWeight(i_h * h_o);
            }

        }
        // sum total weight to input neurons.
        double sum = 0;
        for (FeatureRank rank : getFeatures() ) {
            sum += Math.abs(rank.getTotalWeight());
        }

        // calculate each feature's importance percent
        for (FeatureRank rank : getFeatures()) {
            rank.setImportancePercent(Math.abs(rank.getTotalWeight()) / sum);
        }
    }


    @Override
    public void performRanking(MLDataSet theDataset) {
        throw new EncogError("This ranking algorithm cannot rank relative to a dataset.  Please call peformRanking with no arguments.");
    }

    public double calculateDeviation() {
        double sum = 0;
        for (FeatureRank rank : getFeatures()) {
            sum += rank.getImportancePercent();
        }
        double mean = sum / getFeatures().size();

        sum = 0;
        for (FeatureRank rank : getFeatures()) {
            double d = mean - rank.getImportancePercent();
            sum += d * d;
        }
        return Math.sqrt(sum / getFeatures().size());
    }
}

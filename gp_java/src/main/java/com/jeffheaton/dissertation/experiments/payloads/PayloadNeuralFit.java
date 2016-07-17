package com.jeffheaton.dissertation.experiments.payloads;

import com.jeffheaton.dissertation.experiments.data.ExperimentDatasets;
import com.jeffheaton.dissertation.experiments.manager.ExperimentTask;
import com.jeffheaton.dissertation.experiments.manager.ThreadedRunner;
import com.jeffheaton.dissertation.util.*;
import org.encog.engine.network.activation.ActivationLinear;
import org.encog.engine.network.activation.ActivationReLU;
import org.encog.engine.network.activation.ActivationSoftMax;
import org.encog.mathutil.error.ErrorCalculation;
import org.encog.mathutil.error.ErrorCalculationMode;
import org.encog.mathutil.randomize.XaiverRandomizer;
import org.encog.mathutil.randomize.generate.GenerateRandom;
import org.encog.mathutil.randomize.generate.MersenneTwisterGenerateRandom;
import org.encog.ml.MLRegression;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.end.EarlyStoppingStrategy;
import org.encog.neural.error.CrossEntropyErrorFunction;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.back.Backpropagation;
import org.encog.neural.networks.training.propagation.sgd.StochasticGradientDescent;
import org.encog.neural.networks.training.propagation.sgd.update.AdaGradUpdate;
import org.encog.util.Format;
import org.encog.util.Stopwatch;

/**
 * Created by jeff on 6/12/16.
 */
public class PayloadNeuralFit extends AbstractExperimentPayload {

    public static final int MINI_BATCH_SIZE = 50;
    public static final double LEARNING_RATE = 1e-2;
    public static final int STAGNANT_NEURAL = 50;
    public static final double L1 = 0;
    public static final double L2 = 1e-8;

    private void statusNeural(ExperimentTask task, MLTrain train, EarlyStoppingStrategy earlyStop) {
        StringBuilder line = new StringBuilder();

        line.append("Epoch #");
        line.append(train.getIteration());
        line.append(" Train Error:");
        line.append(Format.formatDouble(train.getError(), 6));
        line.append(", Validation Error: ");
        line.append(Format.formatDouble(earlyStop.getValidationError(), 6));
        line.append(", Stagnant: ");
        line.append(earlyStop.getStagnantIterations());
        task.log(line.toString());
    }

    @Override
    public PayloadReport run(ExperimentTask task) {
        Stopwatch sw = new Stopwatch();
        sw.start();
        // get the dataset
        MLDataSet dataset = ExperimentDatasets.getInstance().loadDatasetNeural(
                task.getDatasetFilename(),
                task.getModelType().getTarget(),
                ArrayUtils.string2list(task.getPredictors())).getData();

        // split
        GenerateRandom rnd = new MersenneTwisterGenerateRandom(42);
        org.encog.ml.data.MLDataSet[] split = Transform.splitTrainValidate(dataset, rnd, 0.75);
        MLDataSet trainingSet = split[0];
        MLDataSet validationSet = split[1];

        // create a neural network, without using a factory
        BasicNetwork network = new BasicNetwork();
        network.addLayer(new BasicLayer(null, true, trainingSet.getInputSize()));
        network.addLayer(new BasicLayer(new ActivationReLU(), true, 200));
        network.addLayer(new BasicLayer(new ActivationReLU(), true, 100));
        network.addLayer(new BasicLayer(new ActivationReLU(), true, 25));

        if (task.getModelType().isRegression()) {
            network.addLayer(new BasicLayer(new ActivationLinear(), false, trainingSet.getIdealSize()));
            //ErrorCalculation.setMode(ErrorCalculationMode.RMS);
        } else {
            network.addLayer(new BasicLayer(new ActivationSoftMax(), false, trainingSet.getIdealSize()));
            //ErrorCalculation.setMode(ErrorCalculationMode.HOT_LOGLOSS);
        }
        network.getStructure().finalizeStructure();
        (new XaiverRandomizer()).randomize(network);
        //network.reset();

        // train the neural networks
        StochasticGradientDescent train = new StochasticGradientDescent(network, trainingSet);
        train.setUpdateRule(new AdaGradUpdate());
        train.setBatchSize(MINI_BATCH_SIZE);
        train.setL1(L1);
        train.setL2(L2);
        train.setLearningRate(LEARNING_RATE);

        EarlyStoppingStrategy earlyStop = new EarlyStoppingStrategy(validationSet, 10, STAGNANT_NEURAL, 0.01);
        earlyStop.setSaveBest(true);
        train.addStrategy(earlyStop);

        long lastUpdate = System.currentTimeMillis();

        do {
            train.iteration();

            long sinceLastUpdate = (System.currentTimeMillis() - lastUpdate) / 1000;

            if (isVerbose() || train.getIteration() == 1 || train.isTrainingDone() || sinceLastUpdate > 60) {
                statusNeural(task, train, earlyStop);
                lastUpdate = System.currentTimeMillis();
            }

            if (Double.isNaN(train.getError()) || Double.isInfinite(train.getError())) {
                break;
            }

        } while (!train.isTrainingDone());
        train.finishTraining();

        NormalizedError error = new NormalizedError(validationSet);
        MLRegression bestNetwork = earlyStop.getBestModel()==null?network:earlyStop.getBestModel();
        double normalizedError = error.calculateNormalizedRange(validationSet,bestNetwork);

        sw.stop();

        task.log("Normalized error: " + normalizedError);

        return new PayloadReport(
                (int) (sw.getElapsedMilliseconds() / 1000),
                normalizedError, earlyStop.getValidationError(), 0, 0,
                train.getIteration(), "");
    }
}

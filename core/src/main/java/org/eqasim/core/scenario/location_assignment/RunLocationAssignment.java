package org.eqasim.core.scenario.location_assignment;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.eqasim.core.location_assignment.matsim.discretizer.FacilityTypeDiscretizerFactory;
import org.eqasim.core.location_assignment.matsim.solver.MATSimAssignmentSolver;
import org.eqasim.core.location_assignment.matsim.solver.MATSimAssignmentSolverBuilder;
import org.eqasim.core.misc.InteractionStageActivityTypes;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;

public class RunLocationAssignment {
	static public void main(String[] args)
			throws IOException, InterruptedException, ExecutionException, ConfigurationException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("population-path", "facilities-path", "quantiles-path", "distributions-path",
						"output-path") //
				.allowOptions("threads", "discretization-iterations", "random-seed", "batch-size") //
				.build();

		// Setting up activity types
		Set<String> relevantActivityTypes = new HashSet<>(Arrays.asList("leisure", "shop", "other"));

		// Setting up modes
		Map<String, Double> discretizationThresholds = new HashMap<>();
		discretizationThresholds.put("car", 200.0);
		discretizationThresholds.put("car_passenger", 200.0);
		discretizationThresholds.put("pt", 200.0);
		discretizationThresholds.put("bike", 100.0);
		discretizationThresholds.put("walk", 100.0);

		// Set up random seed
		int randomSeed = cmd.getOption("random-seed").map(Integer::parseInt).orElse(0);
		Random random = new Random(randomSeed);

		// Load population and facilities
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);

		new PopulationReader(scenario).readFile(cmd.getOptionStrict("population-path"));
		new MatsimFacilitiesReader(scenario).readFile(cmd.getOptionStrict("facilities-path"));

		// Set up data supply
		FacilityTypeDiscretizerFactory discretizerFactory = new FacilityTypeDiscretizerFactory(relevantActivityTypes);
		discretizerFactory.loadFacilities(scenario.getActivityFacilities());

		File quantilesPath = new File(cmd.getOptionStrict("quantiles-path"));
		File distributionsPath = new File(cmd.getOptionStrict("distributions-path"));

		DistanceSamplerFactory distanceSamplerFactory = new DistanceSamplerFactory(random);
		distanceSamplerFactory.load(quantilesPath, distributionsPath);

		StageActivityTypes stageActivityTypes = new InteractionStageActivityTypes();

		// Set up solver
		ProblemProvider problemProvider = new ProblemProvider(distanceSamplerFactory, discretizerFactory,
				discretizationThresholds);

		MATSimAssignmentSolverBuilder builder = new MATSimAssignmentSolverBuilder();

		builder.setVariableActivityTypes(relevantActivityTypes);
		builder.setRandom(random);
		builder.setStageActivityTypes(stageActivityTypes);

		builder.setDiscretizerProvider(problemProvider);
		builder.setDistanceSamplerProvider(problemProvider);
		builder.setDiscretizationThresholdProvider(problemProvider);

		int discretizationIterations = cmd.getOption("discretization-iterations").map(Integer::parseInt).orElse(1000);
		builder.setMaximumDiscretizationIterations(discretizationIterations);

		MATSimAssignmentSolver solver = builder.build();

		// Run assignment
		int batchSize = cmd.getOption("batch-size").map(Integer::parseInt).orElse(1000);
		int numberOfThreads = cmd.getOption("threads").map(Integer::parseInt)
				.orElse(Runtime.getRuntime().availableProcessors());

		LocationAssignment locationAssignment = new LocationAssignment(solver, numberOfThreads, batchSize);
		locationAssignment.run(scenario.getPopulation());

		// Write population
		new PopulationWriter(scenario.getPopulation()).write(cmd.getOptionStrict("output-path"));
	}
}

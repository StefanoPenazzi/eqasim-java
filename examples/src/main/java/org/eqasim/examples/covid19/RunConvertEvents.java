package org.eqasim.examples.covid19;

import org.eqasim.core.components.transit.events.PublicTransitEvent;
import org.eqasim.core.components.transit.events.PublicTransitEventMapper;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class RunConvertEvents {
	static public void main(String[] args) throws ConfigurationException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("input-events-path", "output-events-path", "schedule-path","ipp","inp") //
				.build();

		//Set up config 
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(cmd.getOptionStrict("ipp"));
		config.network().setInputFile(cmd.getOptionStrict("inp"));
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		// Set up vehicle finder
		new TransitScheduleReader(scenario).readFile(cmd.getOptionStrict("schedule-path"));
		VehicleFinder vehicleFinder = new VehicleFinder(scenario.getTransitSchedule());

		// Set up input manager
		EventsManager inputManager = EventsUtils.createEventsManager();

		EventConverter handler = new EventConverter(vehicleFinder,scenario.getPopulation());
		inputManager.addHandler(handler);

		// Read events
		MatsimEventsReader reader = new MatsimEventsReader(inputManager);
		reader.addCustomEventMapper(PublicTransitEvent.TYPE, new PublicTransitEventMapper());
		reader.readFile(cmd.getOptionStrict("input-events-path"));

		// Write events
		// Set up output manager
		EventsManager outputManager = EventsUtils.createEventsManager();
		EventWriterXML writer = new EventWriterXML(cmd.getOptionStrict("output-events-path"));
		outputManager.addHandler(writer);
		handler.replay(outputManager);
		writer.closeFile();
	}
}

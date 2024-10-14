package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

public class CleanUpAdjuster extends MIDIAdjuster {
    /**
     * The default tolerance to use for clean up
     */
    private static final int DEFAULT_TOLERANCE = 10;

    /**
     * The default tick tolerance - 240 is roughly the length of a 16th note
     * - Tick tolerance is used to preserve the last value of groups of notes
     * - We will group our cleanups into groups separate by an absence of events
     * - By the length of this value
     */
    private static final int DEFAULT_TICK_TOLERANCE = 240;

    private static final int INDEX_EVENT_NUMBER_ARG = 0;
    private static final int INDEX_TOLERANCE_ARG = 1;
    private static final int INDEX_TICK_TOLERANCE_ARG = 2;


    /**
     * {@inheritDoc}
     * Expected usage: -c [event number] [tolerance = 10] [tick tolerance = 240]
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        if (transformationArgs.isEmpty() || transformationArgs.size() > 3) {
            System.out.println("ERROR: Incorrect number of args passed to -c (expected 1-3)");
            return -1;
        }

        int tolerance = DEFAULT_TOLERANCE;
        if (transformationArgs.size() > INDEX_TOLERANCE_ARG) {
            tolerance = Integer.parseInt(transformationArgs.get(INDEX_TOLERANCE_ARG));
        }

        long tickTolerance = DEFAULT_TICK_TOLERANCE;
        if (transformationArgs.size() > INDEX_TICK_TOLERANCE_ARG) {
            tickTolerance = Long.parseLong(transformationArgs.get(INDEX_TICK_TOLERANCE_ARG));
        }

        String eventNumberString = transformationArgs.get(INDEX_EVENT_NUMBER_ARG);
        if (eventNumberString.equals(PITCH_BEND_ARG)) {
            cleanUpMidiPitchBends(sequence, tolerance, tickTolerance);
        } else {
            int eventNumber = Integer.parseInt(eventNumberString);
            cleanUpMidiShortMessageEvents(sequence, eventNumber, tolerance, tickTolerance);
        }

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Cleans up midi short message events by deleting events that are too close to their previous value
     * within a given tolerance
     * @param sequence - The sequence to modify
     * @param eventNumber - The number of the event to modify (not used if cleaning up pitch bends)
     * @param tolerance - The tolerance
     */
    private static void cleanUpMidiShortMessageEvents(
            Sequence sequence,
            int eventNumber,
            int tolerance,
            long tickTolerance) {
        cleanUpMidiEvent(sequence, eventNumber, tolerance, tickTolerance);
    }

    /**
     * Cleans up pitch bend events by deleting events that are too close to their previous value
     * within a given tolerance
     * @param sequence - The sequence to modify
     * @param tolerance - The tolerance
     */
    private static void cleanUpMidiPitchBends(
            Sequence sequence,
            int tolerance,
            long tickTolerance) {
        cleanUpMidiEvent(sequence, -1, tolerance, tickTolerance);
    }

    /**
     * Cleans up midi events by deleting events that are too close to their previous value
     * within a given tolerance
     * @param sequence - The sequence to modify
     * @param eventNumber - The number of the event to modify (pass in -1 if cleaning up pitch bends)
     * @param tolerance - The tolerance
     * @param tickTolerance - The tick tolerance - the amount of space between groups of cleaned up notes
     */
    private static void cleanUpMidiEvent(
            Sequence sequence,
            int eventNumber,
            int tolerance,
            long tickTolerance) {
        // This is a pitch bend if we're not given a valid event
        boolean cleanUpPitchBends = eventNumber == -1;

        String eventString = cleanUpPitchBends
            ? "Pitch Bend events"
            : "Event " + eventNumber;

        for (Track track : sequence.getTracks()) {
            int channel = -1;

            ArrayList<MidiEvent> eventsToDelete = new ArrayList<>();
            ArrayList<MidiEvent> events = getRelevantEvents(track, cleanUpPitchBends, eventNumber);
            ArrayList<ArrayList<MidiEvent>> groupedEvents = getGroupedEvents(events, tickTolerance);

            for (ArrayList<MidiEvent> eventGroup : groupedEvents) {
                int lastBaseValue = -1;
                for (MidiEvent e : eventGroup) {
                    MidiMessage msg = e.getMessage();
                    if (msg instanceof ShortMessage) {
                        ShortMessage shortMsg = (ShortMessage) msg;
                        int command = shortMsg.getCommand();
                        channel = shortMsg.getChannel();
                        int data1 = shortMsg.getData1();
                        int data2 = shortMsg.getData2();
                        int value = -1;

                        if (cleanUpPitchBends) {
                            if (command == ShortMessage.PITCH_BEND) {
                                value = PitchBendAdjuster.getPitchBendValue(data1, data2);
                            } else {
                                // We're only looking for pitch bend commands here
                                continue;
                            }
                        } else if (command == ShortMessage.CONTROL_CHANGE && data1 == eventNumber) {
                            value = data2;
                        } else {
                            // This is not the event we're looking for
                            continue;
                        }

                        // We never want two events in a row with the same value
                        // It also says nothing about the current direction, so just continue
                        if (value == lastBaseValue) {
                            eventsToDelete.add(e);
                            continue;
                        }

                        // The very first time this runs - just set the base value
                        if (lastBaseValue == -1) {
                            lastBaseValue = value;
                            continue;
                        }

                        // If the value is outside the allowed tolerance, mark it for deletion
                        if (!isValueWithinTolerance(lastBaseValue, value, tolerance)) {
                            eventsToDelete.add(e);

                            String eventDelString = cleanUpPitchBends
                                    ? "Pitch Bend event"
                                    : "event " + eventNumber;
                            verboseLog("Deleted " + eventDelString + " at tick " + e.getTick(), channel);
                        }

                        // Otherwise, we've kept the event, so update the base value
                        else {
                            lastBaseValue = value;
                        }
                    }
                }
            }

            int numberOfEventsToDelete = eventsToDelete.size();
            deleteEventsFromTrack(track, eventsToDelete);
            if (numberOfEventsToDelete > 0) {
                System.out.println("Channel " + (channel + 1) + ": " + eventsToDelete.size() + " " + eventString + " cleaned up.");
            }
        }
    }

    private static ArrayList<MidiEvent> getRelevantEvents(Track track, boolean cleanUpPitchBends, int eventNumber) {
        ArrayList<MidiEvent> events = new ArrayList<>();
        int expectedCommand = cleanUpPitchBends
            ? ShortMessage.PITCH_BEND
            : ShortMessage.CONTROL_CHANGE;
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();
            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage) msg;
                int command = shortMsg.getCommand();
                int data1 = shortMsg.getData1();

                if (command == expectedCommand) {
                    // Pitch bends don't care about the data1, since it's part of its value
                    if (command == ShortMessage.PITCH_BEND || data1 == eventNumber) {
                        events.add(e);
                    }
                }
            }
        }
        return events;
    }

    /**
     * Gets a list of groups of events for parsing, based on the tick tolerance
     * @param events - The list of events we're comparing
     * @param tickTolerance - The tick tolerance
     */
    private static ArrayList<ArrayList<MidiEvent>> getGroupedEvents(ArrayList<MidiEvent> events, long tickTolerance) {
        // Nothing to do if there's no events
        if (events.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<ArrayList<MidiEvent>> groupsOfEvents = new ArrayList<>();
        ArrayList<MidiEvent> currentGroup = null;
        long lastTick = -1;
        for (MidiEvent event : events) {
            long tick = event.getTick();

            // Start a new group if it's the first event being looked at OR
            // If it does not pass tolerance
            if (lastTick == -1 || lastTick + tickTolerance <= tick) {
                currentGroup = new ArrayList<>();
                groupsOfEvents.add(currentGroup);
            }

            currentGroup.add(event);
            lastTick = tick;
        }

        // Remove the last element from all the groups, as we need to preserve the last
        // value since it will be in effect until the next event
        for (ArrayList<MidiEvent> eventGroup : groupsOfEvents) {
            if (!eventGroup.isEmpty()) {
                eventGroup.remove(eventGroup.get(eventGroup.size() - 1));
            }
        }

        return groupsOfEvents;
    }

    /**
     * Checks whether the current value is in range of the base value, within a certain tolerance
     * - Example: Base value is 100; current is 110; tolerance is 15
     * -  Because 100 is only 10 away from 110, it passes with a tolerance of 15
     * -  It would NOT pass if the tolerance was 5
     * @param baseValue - The base value
     * @param currentValue - The current value to check against the base
     * @param tolerance - The tolerance
     * @return True if it's within range; false otherwise
     */
    private static boolean isValueWithinTolerance(int baseValue, int currentValue, int tolerance) {
        // The current value is greater and outside the allowed range
        if (currentValue > baseValue &&
                baseValue + tolerance > currentValue) {
            return false;
        }

        // The current value is smaller and outside the allowed range
        if (currentValue < baseValue &&
                baseValue - tolerance < currentValue) {
            return false;
        }

        return true;
    }
}

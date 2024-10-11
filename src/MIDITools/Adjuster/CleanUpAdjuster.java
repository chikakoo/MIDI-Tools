package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

public class CleanUpAdjuster extends MIDIAdjuster {
    /**
     * Cleans up midi events by deleting events that are too close to their previous value
     * within a given tolerance
     * @param sequence - The sequence to modify
     * @param eventNumber - The number of the event to modify (not used if cleaning up pitch bends)
     * @param tolerance - The tolerance
     * @param cleanUpPitchBend - If true, cleans up pitch bends instead of the given event number
     */
    public static void cleanUpMidiEvent(
            Sequence sequence,
            int eventNumber,
            int tolerance,
            boolean cleanUpPitchBend) {
        ArrayList<String> channelsCleanedUp = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            ArrayList<MidiEvent> eventsToDelete = new ArrayList<>();
            int lastBaseValue = -1;
            int channel = -1;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();
                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    channel = shortMsg.getChannel();
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();
                    int value = -1;

                    if (cleanUpPitchBend) {
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

                        String eventString = cleanUpPitchBend
                                ? "Pitch Bend event"
                                : "event " + eventNumber;
                        verboseLog("Deleted " + eventString + " at tick " + e.getTick(), channel);
                    }

                    // Otherwise, we've kept the event, so update the base value
                    else {
                        lastBaseValue = value;
                    }
                }
            }

            if (!eventsToDelete.isEmpty()) {
                channelsCleanedUp.add(String.valueOf(channel + 1));
            }
            deleteEventsFromTrack(track, eventsToDelete);
        }

        String eventString = cleanUpPitchBend
                ? "Pitch Bend events"
                : "Event " + eventNumber;
        showChannelsModifiedMessage(channelsCleanedUp, eventString + " cleaned up on channels");
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

package MIDITools.Adjuster;

import MIDITools.MIDITools;

import javax.sound.midi.*;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class PitchBendAdjuster extends MIDIAdjuster {
    /**
     * Anvil Studio uses the "Data Slider" event for this
     * It uses the CONTROL_CHANGE message, with a Data 1 value of 6
     */
    private static final int PITCH_BEND_RANGE_DATA = 6;

    /**
     * Needed when making a new event for pitch bends
     * These events must be there after the pitch bend range
     */
    private static final int REGISTERED_PARAM_MSB = 101;
    private static final int REGISTERED_PARAM_LSB = 100;
    private static final int DATA_SLIDER_LSB = 38;
    private static final int DEFAULT_DATA_2 = 0;

    /**
     * OoT assumes the pitch bend range is the full octave (a range of 12)
     * Anvil Studio assumes it's 2 if no range is explicitly set
     */
    private static final int DESIRED_PITCH_BEND_RANGE = 12;

    /**
     * Modifies the pitch bends
     * - This does not handle pitch bend range changes in the middle of the track very well
     * - Try to scan for this and adjust the midi accordingly before running this
     * @param sequence - The sequence to modify
     */
    public static void editMidiPitchBends(Sequence sequence) {
        ArrayList<String> pitchBendRangeMessages = new ArrayList<>();
        ArrayList<String> channelsWithAdjustments = new ArrayList<>();

        boolean adjustedAnyBends = false;
        for (Track track : sequence.getTracks()) {
            // If the channel already has the desired pitch bend range, then go to the next track

            double bendFactor = tryGetNewPitchBendRangeFactor(track, pitchBendRangeMessages);
            if (bendFactor == 0) {
                // In this case, we're already using the default, so no need to continue
                continue;
            }

            // Default to this and then change it as the change events are found
            int lastChannelAdjusted = -1;
            boolean adjustedBendForThisTrack = false;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();
                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage)msg;

                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    int anvilStudioChannel = channel + 1;

                    if (command == ShortMessage.PITCH_BEND) {
                        // We're choosing not to display bend values that didn't actually change
                        if (adjustPitchBend(shortMsg, bendFactor) && !adjustedBendForThisTrack) {
                            channelsWithAdjustments.add(anvilStudioChannel + "");
                            lastChannelAdjusted = channel;
                            adjustedBendForThisTrack = true;
                            adjustedAnyBends = true;
                        }
                    }
                }
            }

            // If we have adjustments, but haven't fixed the range, we need to add the data event to set
            // the value to 12 so that it will sound correct
            if (adjustedBendForThisTrack && lastChannelAdjusted >= 0) {
                cleanUpPitchBendRangeEvents(track);
                createPitchBendRangeEvents(track, lastChannelAdjusted, pitchBendRangeMessages);
            }
        }

        if (!adjustedAnyBends) {
            MIDIAdjuster.printMessages(pitchBendRangeMessages);
            System.out.println("Did not find any pitch bends to adjust.");
            return;
        }

        // Print out a summary
        System.out.println();
        MIDIAdjuster.printMessages(pitchBendRangeMessages);
        MIDIAdjuster.showChannelsModifiedMessage(channelsWithAdjustments, "Channels adjusted");
        System.out.println();
    }

    /**
     * Attempts to get the new pitch bend range factor, based on the first pitch bend range event we find
     * - If we find no event, return the default
     * - If it's the current desired range, then return 0
     * @param track - The track to check
     * @param pitchBendRangeMessages - A list of messages to display for pitch bends - adds to it if we're skipping
     * @return A double indicating the new pitch bend range factor, or 0 if we're not adjusting
     */
    private static double tryGetNewPitchBendRangeFactor(
            Track track,
            ArrayList<String> pitchBendRangeMessages) {
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage)msg;
                int command = shortMsg.getCommand();
                int data1 = shortMsg.getData1();
                int data2 = shortMsg.getData2();

                // Adjust the bend factor both for this program and in Anvil Studio
                if (command == ShortMessage.CONTROL_CHANGE && data1 == PITCH_BEND_RANGE_DATA) {
                    if (data2 == DESIRED_PITCH_BEND_RANGE) {
                        return 0;
                    } else {
                        return getNewPitchBendRangeFactor(shortMsg, pitchBendRangeMessages);
                    }
                }
            }
        }

        // If it gets here, no message was found, so adjust by the default factor
        // The bend factor is equal to the desired range divided by what the range is now
        // - So, if both are the same, the bend factor is 1, meaning no adjustments needed
        return (double)DESIRED_PITCH_BEND_RANGE / MIDITools.defaultPitchBendRange;
    }

    /**
     * CLeans up all pitch bend range events, as songs often have dups of these
     * @param track - The track to clean up
     */
    private static void cleanUpPitchBendRangeEvents(Track track) {
        ArrayList<MidiEvent> eventsToRemove = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage)msg;
                int command = shortMsg.getCommand();
                int data1 = shortMsg.getData1();

                // Adjust the bend factor both for this program and in Anvil Studio
                if ((command == ShortMessage.CONTROL_CHANGE && data1 == PITCH_BEND_RANGE_DATA) ||
                        (command == ShortMessage.CONTROL_CHANGE && data1 == REGISTERED_PARAM_MSB) ||
                        (command == ShortMessage.CONTROL_CHANGE && data1 == REGISTERED_PARAM_LSB) ||
                        (command == ShortMessage.CONTROL_CHANGE && data1 == DATA_SLIDER_LSB)) {
                    eventsToRemove.add(e);
                }
            }
        }

        MIDIAdjuster.deleteEventsFromTrack(track, eventsToRemove);
    }

    /**
     * Creates a new set of pitch bend range events
     * @param track - The track to create the events for
     * @param lastChannelAdjusted - the last channel adjusted
     * @param pitchBendRangeMessages - the pitch bend range messages to add to
     */
    private static void createPitchBendRangeEvents(
            Track track,
            int lastChannelAdjusted,
            ArrayList<String> pitchBendRangeMessages) {
        ShortMessage pitchBendRangeMessage = new ShortMessage();
        ShortMessage registeredParamMSB = new ShortMessage();
        ShortMessage registeredParamLSB = new ShortMessage();
        ShortMessage dataSliderLSBMessage = new ShortMessage();

        MIDIAdjuster.setShortMessage(pitchBendRangeMessage, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, PITCH_BEND_RANGE_DATA, DESIRED_PITCH_BEND_RANGE);
        MIDIAdjuster.setShortMessage(registeredParamMSB, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, REGISTERED_PARAM_MSB, DEFAULT_DATA_2);
        MIDIAdjuster.setShortMessage(registeredParamLSB, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, REGISTERED_PARAM_LSB, DEFAULT_DATA_2);
        MIDIAdjuster.setShortMessage(dataSliderLSBMessage, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, DATA_SLIDER_LSB, DEFAULT_DATA_2);

        track.add(new MidiEvent(registeredParamMSB, 0));
        track.add(new MidiEvent(registeredParamLSB, 0));
        track.add(new MidiEvent(pitchBendRangeMessage, 0));
        track.add(new MidiEvent(dataSliderLSBMessage, 0));

        pitchBendRangeMessages.add("Channel " + (lastChannelAdjusted + 1) + ": Created new pitch bend range with a value of 12");
    }

    /**
     * Gets what the new pitch bend range factor should be
     * @param shortMsg - the message to base the range factor off of
     * @param pitchBendRangeMessages - an array of messages to potentially add to
     * @return The bend factor to use
     */
    private static double getNewPitchBendRangeFactor(ShortMessage shortMsg, ArrayList<String> pitchBendRangeMessages) {
        int channel = shortMsg.getChannel();
        int anvilStudioChannel = channel + 1;
        int oldPitchBendRange = shortMsg.getData2();

        // Some MIDIs have a value greater than 12, which is invalid
        // Same story for those with a value less than 1
        // Anvil Studio treats it as a 2, so we will do the same
        double bendFactor = (oldPitchBendRange > DESIRED_PITCH_BEND_RANGE) && (oldPitchBendRange > 0)
                ? (double)DESIRED_PITCH_BEND_RANGE / MIDITools.defaultPitchBendRange
                : (double)DESIRED_PITCH_BEND_RANGE / oldPitchBendRange;

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        pitchBendRangeMessages.add("Channel " + anvilStudioChannel + ": Adjusted pitch bend range from " + oldPitchBendRange + " to " + DESIRED_PITCH_BEND_RANGE + " which is a factor of " + df.format(bendFactor));

        return bendFactor;
    }

    /**
     * Adjusts the pitch bend value on the message
     * @param shortMsg - the message to adjust
     * @param bendFactor - the amount to adjust the bend by - this is the desired value divided by the current one
     *                   Normally 12 divided by whatever it's set in Anvil Studio at the moment
     * @return A boolean indicating whether we actually adjusted the value
     */
    private static boolean adjustPitchBend(ShortMessage shortMsg, double bendFactor) {
        final int BASE_VALUE = 8192;

        int channel = shortMsg.getChannel();
        int value = getPitchBendValue(shortMsg.getData1(), shortMsg.getData2());

        int valueToAdjust = value - BASE_VALUE; // Get the difference of 8192 off the value
        int adjustedValue = (int)(valueToAdjust / bendFactor); // Adjust that difference

        int newValue = adjustedValue + BASE_VALUE; // Add the adjusted value back to the base
        int newData2 = newValue / 128;
        int newData1 = newValue % 128;

        MIDIAdjuster.setShortMessage(shortMsg, ShortMessage.PITCH_BEND, channel, newData1, newData2);

        int realNewValue = getPitchBendValue(newData1, newData2);
        if (realNewValue != BASE_VALUE) {
            MIDIAdjuster.verboseLog("Adjusting pitch bend value " + value + " to be " + realNewValue, channel);
            return true;
        }

        return false;
    }

    /**
     * Gets the pitch bend value from the two data points
     * @param data1 - The precise value - simply adds to the pitch bend value
     * @param data2 - Each value of this adds 128 to the pitch bend value
     * @return The pitch bend value as an integer
     */
    public static int getPitchBendValue(int data1, int data2) {
        return data1 + (data2 * 128);
    }
}

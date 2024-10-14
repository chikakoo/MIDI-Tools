package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.*;

public class MIDIEventValueAdjuster extends MIDIAdjuster {
    private static final int INDEX_EVENT_NUMBER_ARG = 0;
    private static final int INDEX_AMOUNT_ARG = 1;
    private static final int INDEX_CHANNEL_ARG = 2;

    /**
     * {@inheritDoc}
     * Expected usage: [-a|-s] [event number] [amount] [channel = -1]
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        String currentFlag = args[currentIndex];
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        if (transformationArgs.size() < 2 || transformationArgs.size() > 3) {
            System.out.println("ERROR: Incorrect number of args passed to -a or -s (expected 2-3)");
            return -1;
        }

        // Grab the amount - make it negative if it's subtraction
        int amount = Integer.parseInt(transformationArgs.get(INDEX_AMOUNT_ARG));
        amount = currentFlag.equals("-s")
            ? -amount
            : amount;

        int channel = -1;
        if (transformationArgs.size() > INDEX_CHANNEL_ARG) {
            channel = Integer.parseInt(transformationArgs.get(INDEX_CHANNEL_ARG));
        }

        String eventNumberString = transformationArgs.get(INDEX_EVENT_NUMBER_ARG);
        if (eventNumberString.equals(PITCH_BEND_ARG)) {
            addOrSubtractPitchBendValue(sequence, amount, channel);
        } else {
            int eventNumber = Integer.parseInt(eventNumberString);
            addOrSubtractShortMessageValue(sequence, eventNumber, amount, channel);
        }

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Adds or subtracts the given amount from all given short message event numbers
     * @param sequence - The sequence to modify
     * @param amount - The amount to modify by - negative number to subtract
     * @param channelToModify - The channel to modify (if given negative, runs for all channels)
     */
    private static void addOrSubtractShortMessageValue(
            Sequence sequence,
            int eventNumber,
            int amount,
            int channelToModify) {
        addOrSubtractMidiEventValue(sequence, eventNumber, amount, channelToModify);
    }

    /**
     * Adds or subtracts the given amount from all pitch bends
     * @param sequence - The sequence to modify
     * @param amount - The amount to modify by - negative number to subtract
     * @param channelToModify - The channel to modify (if given negative, runs for all channels)
     */
    private static void addOrSubtractPitchBendValue(
            Sequence sequence,
            int amount,
            int channelToModify) {
        addOrSubtractMidiEventValue(sequence, -1, amount, channelToModify);
    }

    /**
     * Adds or subtracts the given amount from all the given midi events
     * @param sequence - The sequence to modify
     * @param eventNumber - The event number (if not modifying pitch bends)
     * @param amount - The amount to modify by - negative number to subtract
     * @param channelToModify - The channel to modify (if given negative, runs for all channels)
     */
    private static void addOrSubtractMidiEventValue(
            Sequence sequence,
            int eventNumber,
            int amount,
            int channelToModify) {
        // This is a pitch bend if we're not given a valid event
        boolean modifyPitchBendEvent = eventNumber == -1;

        Set<String> channelsAdjusted = new HashSet<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();

                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    int anvilStudioChannel = channel + 1;
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();

                    if (channelToModify >= 0 && anvilStudioChannel != channelToModify) {
                        continue;
                    }

                    if (modifyPitchBendEvent) {
                        if (command == ShortMessage.PITCH_BEND) {
                            channelsAdjusted.add(String.valueOf(channel + 1));
                            int oldPitchBendValue = PitchBendAdjuster.getPitchBendValue(data1, data2);
                            int newPitchBendValue = oldPitchBendValue + amount;

                            int newData1 = oldPitchBendValue % 128;
                            int newData2 = oldPitchBendValue / 128;
                            setShortMessage(shortMsg, ShortMessage.PITCH_BEND, channel, newData1, newData2);

                            String valueString = oldPitchBendValue + " -> " + newPitchBendValue;
                            verboseLog("Pitch Bend event " + valueString + " at tick " + e.getTick(), channel);
                        }
                    } else if (command == ShortMessage.CONTROL_CHANGE && data1 == eventNumber) {
                        channelsAdjusted.add(String.valueOf(channel + 1));
                        int oldEventValue = data2;
                        int newEventValue = oldEventValue + amount;
                        setShortMessage(shortMsg, ShortMessage.CONTROL_CHANGE, channel, eventNumber, newEventValue);

                        String valueString = oldEventValue + " -> " + newEventValue;
                        verboseLog("Event " + eventNumber + " - " + valueString + " at tick " + e.getTick(), channel);
                    }
                }
            }
        }

        String eventString = modifyPitchBendEvent
                ? "Pitch Bend events"
                : "Event " + eventNumber;
        showChannelsModifiedMessage(new ArrayList<>(channelsAdjusted), eventString + " changed by " + amount + " on channels");
    }
}

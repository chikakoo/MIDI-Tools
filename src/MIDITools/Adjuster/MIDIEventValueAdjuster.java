package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MIDIEventValueAdjuster extends MIDIAdjuster {
    /**
     * Adds or subtracts the given amount from the given midi event
     * @param sequence - The sequence to modify
     * @param eventNumber - The event number (if not modifying pitch bends)
     * @param amount - The amount to modify by - negative number to subtract
     * @param modifyPitchBendEvent - Whether this is modifying a pitch bend event
     * @param channelToModify - The channel to modify (if given negative, runs for all channels)
     */
    public static void addOrSubtractMidiEventValue(
            Sequence sequence,
            int eventNumber,
            int amount,
            boolean modifyPitchBendEvent,
            int channelToModify) {
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

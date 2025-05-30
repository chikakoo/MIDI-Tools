package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;
import java.util.HashSet;

public class ExpressionAdjuster extends MIDIAdjuster {
    private static final int VOLUME_EVENT = 7;
    private static final int EXPRESSION_EVENT = 11;

    /**
     * {@inheritDoc}
     * Expected usage: -e
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        if (!transformationArgs.isEmpty()) {
            System.out.println("ERROR: Incorrect number of args passed (expected 0)");
            return -1;
        }

        editMidiExpression(sequence);

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Converts expression events into volume events
     * @param sequence - The sequence to modify
     */
    public void editMidiExpression(Sequence sequence) {
        HashSet<String> channelsAffected = new HashSet<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();
                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage)msg;

                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();

                    if (command == ShortMessage.CONTROL_CHANGE) {
                        int data1 = shortMsg.getData1();
                        int data2 = shortMsg.getData2();
                        if (data1 == EXPRESSION_EVENT) {
                            setShortMessage(shortMsg, command, channel, VOLUME_EVENT, data2);
                            channelsAffected.add((channel + 1) + "");
                        }
                    }
                }
            }
        }

        // Print out a summary
        System.out.println();

        if (channelsAffected.isEmpty()) {
            System.out.println("Did not find any expression events.");
        } else {
            MIDIAdjuster.showChannelsModifiedMessage(new ArrayList<>(channelsAffected), "Channels adjusted");
        }

        System.out.println();
    }
}

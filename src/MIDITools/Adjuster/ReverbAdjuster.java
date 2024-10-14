package MIDITools.Adjuster;

import javax.sound.midi.Sequence;
import java.util.ArrayList;

public class ReverbAdjuster extends EventReplacer {
    /**
     * The default range to use for reverb - 5 seems to be the equivalent
     * of OoT's 127, but this is situational
     */
    public final static double DEFAULT_RANGE = 26;
    private static final int REVERB_SEND_LEVEL_EVENT = 91;

    private static final int INDEX_RANGE_ARG = 0;

    /**
     * {@inheritDoc}
     * Expected usage: -r [reverb range = 26]
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        if (transformationArgs.size() > 1) {
            System.out.println("ERROR: Incorrect number of args passed to -r (expected 0-1)");
            return -1;
        }

        double range = DEFAULT_RANGE;
        if (!transformationArgs.isEmpty()) {
            range = Double.parseDouble(transformationArgs.get(INDEX_RANGE_ARG));
        }

        editMidiReverb(sequence, range);

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Converts all ReverbSendLevel events to the given range
     * - Will convert it to the closest value in that range
     * - Examples:
     *   - A range of 5 and a value of 10 will give back a value of 1
     *   - A range of 26 and a value of 127 will give back a value of 26
     * @param sequence - The sequence to modify
     * @param range - The max value the ReverbSendLevel should have
     */
    private void editMidiReverb(Sequence sequence, double range) {
        // Note that we're replacing the event with one of the same type here (just with a different value)
        replaceMidiEvents(
            sequence,
            REVERB_SEND_LEVEL_EVENT,
            REVERB_SEND_LEVEL_EVENT,
            range,
            "Reverb");
    }
}

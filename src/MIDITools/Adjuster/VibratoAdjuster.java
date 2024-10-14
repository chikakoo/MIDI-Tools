package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

public class VibratoAdjuster extends EventReplacer {
    /**
     * The default range to use for vibrato - 5 seems to be the equivalent
     * of OoT's 127, but this is situational
     */
    public final static double DEFAULT_RANGE = 5;

    private static final int VIBRATO_DEPTH_EVENT = 77;
    private static final int MODULATION_EVENT = 1;

    private static final int INDEX_RANGE_ARG = 0;

    /**
     * {@inheritDoc}
     * Expected usage: -v [vibrato range = 5]
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        if (transformationArgs.size() > 1) {
            System.out.println("ERROR: Incorrect number of args passed to -v (expected 0-1)");
            return -1;
        }

        double range = DEFAULT_RANGE;
        if (!transformationArgs.isEmpty()) {
            range = Double.parseDouble(transformationArgs.get(INDEX_RANGE_ARG));
        }

        editMidiVibrato(sequence, range);

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Converts modulation events into vibrato depth events (that's what seq 64 uses)
     * - Will convert it to the closest value to the defaultVibratoRange (rounded up)
     * - For example, a range of 5 and a modulation of 10 will give a vibrato depth of 1
     * @param sequence - The sequence to modify
     * @param range - The max value the vibrato depth should have
     */
    public void editMidiVibrato(Sequence sequence, double range) {
        replaceMidiEvents(
            sequence,
            MODULATION_EVENT,
            VIBRATO_DEPTH_EVENT,
            range,
            "Vibrato Depth");
    }
}

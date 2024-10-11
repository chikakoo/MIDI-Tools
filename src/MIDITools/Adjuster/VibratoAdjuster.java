package MIDITools.Adjuster;

import javax.sound.midi.*;

public class VibratoAdjuster extends MIDIAdjuster {
    /**
     * The default range to use for vibrato - 5 seems to be the equivalent
     * of OoT's 127, but this is situational
     */
    public final static double DEFAULT_RANGE = 5;

    private static final int VIBRATO_DEPTH_EVENT = 77;
    private static final int MODULATION_EVENT = 1;

    /**
     * Converts modulation events into vibrato depth events (that's what seq 64 uses)
     * - Will convert it to the closest value to the defaultVibratoRange (rounded up)
     * - For example, a range of 5 and a modulation of 10 will give a vibrato depth of 1
     * @param sequence - The sequence to modify
     * @param range - The max value the vibrato depth should have
     */
    public static void editMidiVibrato(Sequence sequence, double range) {
        new EventReplacer().replaceMidiEvents(
            sequence,
            MODULATION_EVENT,
            VIBRATO_DEPTH_EVENT,
            range,
            "Vibrato Depth");
    }
}

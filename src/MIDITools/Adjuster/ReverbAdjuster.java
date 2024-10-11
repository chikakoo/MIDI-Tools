package MIDITools.Adjuster;

import javax.sound.midi.Sequence;

public class ReverbAdjuster {
    /**
     * The default range to use for reverb - 5 seems to be the equivalent
     * of OoT's 127, but this is situational
     */
    public final static double DEFAULT_RANGE = 26;
    private static final int REVERB_SEND_LEVEL_EVENT = 91;

    /**
     * Converts all ReverbSendLevel events to the given range
     * - Will convert it to the closest value in that range
     * - Examples:
     *   - A range of 5 and a value of 10 will give back a value of 1
     *   - A range of 26 and a value of 127 will give back a value of 26
     * @param sequence - The sequence to modify
     * @param range - The max value the ReverbSendLevel should have
     */
    public static void editMidiReverb(Sequence sequence, double range) {
        // Note that we're replacing the event with one of the same type here (just with a different value)
        new EventReplacer().replaceMidiEvents(
            sequence,
            REVERB_SEND_LEVEL_EVENT,
            REVERB_SEND_LEVEL_EVENT,
            range,
            "Reverb");
    }
}

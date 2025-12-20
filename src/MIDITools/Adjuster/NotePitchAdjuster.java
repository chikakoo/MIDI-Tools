package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

/**
 * Applies a pitch bend to every note in the channel. The value is equal to distance
 * from a given base note value to the note in the channel.
 *
 * Afterward, you must move every note, in Anvil Studio, to the base value. It will
 * then sound like it did before.
 *
 *  Due to note overlap concerns, this will NOT adjust the note values automatically.
 *  It also will not care about chords, so it's important to ensure the channel is
 *  compatible with this before using it.
 *
 * i.e. If given a base value of midi 60, a found note of midi 62 will get a pitch
 * bend up 2 half steps so that if moved to 60, it would play midi 62.
 *
 */
public class NotePitchAdjuster extends MIDIAdjuster {
    /**
     * The default range - equivalent to how much the note will be adjusted per the
     * pitch bend value going either direction, in half steps.
     *
     * So, 12 indicates one octave up, and one octave down (12 half steps each direction).
     */
    private static final double DEFAULT_PITCH_BEND_RANGE = 12;

    private static final int CHANNEL_NUMBER_ARG = 0;
    private static final int BASE_NOTE_ARG = 1;
    private static final int PITCH_BEND_RANGE_ARG = 2;

    /**
     * {@inheritDoc}
     * Expected usage: -n [channel number] [base note] [pitch bend range = 12]
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        int numberOfArgs = transformationArgs.size();
        if (numberOfArgs != 2 && numberOfArgs != 3) {
            System.out.println("ERROR: Incorrect number of args passed to -n (expected 2-3)");
            return -1;
        }

        int channelNumber = Integer.parseInt(transformationArgs.get(CHANNEL_NUMBER_ARG));
        int baseNote = Integer.parseInt(transformationArgs.get(BASE_NOTE_ARG));
        double pitchBendRange = DEFAULT_PITCH_BEND_RANGE;

        if (numberOfArgs == 3) {
            pitchBendRange = Double.parseDouble(transformationArgs.get(PITCH_BEND_RANGE_ARG));
        }

        editMidiPitches(sequence, channelNumber, baseNote, pitchBendRange);

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Applies a pitch bend to every note in the channel. The value is equal to distance
     * from a given base note value to the note in the channel.
     *
     * See the documentation for the main class for more details.
     * @param sequence - the sequence to modify
     * @param channelNumber - the Anvil Studio channel number to apply this to
     * @param baseNote - the base note value to use
     * @param pitchBendRange - the pitch bend range
     */
    private void editMidiPitches(
        Sequence sequence, int channelNumber, int baseNote, double pitchBendRange)
    {
        int currentAdjustment = Integer.MIN_VALUE; // Used so we don't enter dup events
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();

                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    int anvilStudioChannel = channel + 1;

                    if (anvilStudioChannel != channelNumber || command != ShortMessage.NOTE_ON) {
                        continue;
                    }

                    int noteValue = shortMsg.getData1();
                    long tick = e.getTick();

                    // Don't do anything if we're already bending by this much
                    if (currentAdjustment == noteValue - baseNote)  {
                        continue;
                    }
                    currentAdjustment = noteValue - baseNote;

                    int pitchBendValue = getPitchBendAdjustment(pitchBendRange, currentAdjustment, tick);
                    int pitchBendData1 = pitchBendValue % 128;
                    int pitchBendData2 = pitchBendValue / 128;
                    addNewShortMessage(
                        track,
                        ShortMessage.PITCH_BEND,
                        channel,
                        pitchBendData1,
                        pitchBendData2,
                        tick,
                        "Pitch Bend");
                }
            }
        }
    }

    /**
     * Gets the pitch bend value to use to adjust to the given note
     * @param pitchBendRange - the pitch bend range to use
     * @param semitones - the number of semitones from the base note to the target note
     * @param tick - the tick to adjust to - this is just for logging in case something goes wrong
     * @return The pitch bend value to use
     */
    private int getPitchBendAdjustment(double pitchBendRange, int semitones, long tick) {
        final double adjustmentPerSemitone = PitchBendAdjuster.BASE_VALUE / pitchBendRange;
        int result = (int)Math.floor((adjustmentPerSemitone * semitones) + PitchBendAdjuster.BASE_VALUE);

        if (result < 0 || result > (PitchBendAdjuster.BASE_VALUE * 2)) {
            throw new IllegalStateException("At tick " + tick + ", pitch bend value cannot be " + result + " (adjusting by " + semitones + ")");
        }

        return result == 0
            ? 0 // No adjustment for a perfect match, we don't want to return -1
            : result - 1; // -1 because it's essentially 0-indexed
    }
}

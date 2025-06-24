package MIDITools.Adjuster;

public class MIDIAdjusterFactory {
    /**
     * Gets a MIDIAdjuster instance by the given flag
     * @param flag - The flag to get an instance for
     * @return The MIDI adjuster associated with the flag (or null if not found)
     */
    public static MIDIAdjuster getMIDIAdjusterByFlag(String flag) {
        switch(flag) {
            case "-p":
                return new PitchBendAdjuster();
            case "-v":
                return new VibratoAdjuster();
            case "-r":
                return new ReverbAdjuster();
            case "-c":
                return new CleanUpAdjuster();
            case "-e":
                return new ExpressionAdjuster();
            case "-a":
            case "-s":
                return new MIDIEventValueAdjuster();
            case "-m":
                return new EventMover();
            default:
                return null;
        }
    }
}

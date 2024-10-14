package MIDITools;

import MIDITools.Adjuster.*;

import javax.sound.midi.*;
import java.io.*;

public class MIDITools {
    //<editor-fold desc="Constants">

    /**
     * The user has to manually set the verbose flag if they wish to log everything
     * There's potentially a ton of things logged, so this is off by default
     */
    private static final String VERBOSE_FLAG = "--verbose";
    public static boolean verboseLogging = false;

    //</editor-fold>

    //<editor-fold desc="Main / File Writing">

    public static void main(String[] args) {
        if (!validateArgsAndSetVerbosity(args)) {
            System.exit(0);
        }

        String midiFileName = args[0].trim();

        Sequence sequence;
        File midiFile;
        try {
            midiFile = new File(midiFileName);
            sequence = MidiSystem.getSequence(midiFile);
        } catch (IOException | InvalidMidiDataException e) {
            e.printStackTrace();
            return;
        }

        int startingIndex = verboseLogging ? 2 : 1; // The logging flag adds one argument
        processTransformations(args, startingIndex, sequence);

        writeSequenceToFile(midiFileName, midiFile, sequence);
    }

    /**
     * Processes all the transformations, starting at the given index
     * @param args - The args passed in via command line
     * @param startingIndex - The index to start processing from
     * @param sequence - The sequence file to process
     */
    private static void processTransformations(String[] args, int startingIndex, Sequence sequence) {
        int argIndex = startingIndex;
        while (argIndex < args.length) {
            String flag = args[argIndex];
            MIDIAdjuster adjuster = MIDIAdjusterFactory.getMIDIAdjusterByFlag(flag);

            if (adjuster == null) {
                System.out.println("ERROR: Invalid flag " + flag + " at index " + argIndex + ".");
                System.exit(0);
            }

            argIndex = adjuster.execute(args, argIndex, sequence);
            if (argIndex == -1) {
                // We would have already displayed the main error at this point, so just show usage and exit
                showUsage();
                System.exit(0);
            }
        }
    }

    /**
     * Validates the arguments and sets the verbosity flag
     * - If no verbosity flag, validates there's a filename and at least one other parameter
     * - If verbosity flag, validates there's a filename and at least noe parameter after that flag
     * @param args - The given command line arguments
     * @return True if validation was successful; false otherwise
     */
    private static boolean validateArgsAndSetVerbosity(String[] args) {
        // There must be at least 2 args
        boolean areArgsValid = args.length >= 2 && !args[0].trim().isEmpty();

        // Check whether the second argument is the verbose flag and set it if so
        if (areArgsValid && args[1].trim().equals(VERBOSE_FLAG)) {
            verboseLogging = true;

            // If there's a verbose flag, we need one more argument!
            if (args.length < 3) {
                areArgsValid = false;
            }
        }

        if (!areArgsValid) {
            showUsage("ERROR: The midi filename and at least one transformation is required.");
        }

        return areArgsValid;
    }

    /**
     * Shows the usage description
     */
    private static void showUsage() {
        showUsage("");
    }

    /**
     * Shows the usage description
     * @param error - The reason usage is being shown
     */
    private static void showUsage(String error) {
        if (!error.isEmpty()) {
            System.out.println(error);
            System.out.println();
        }

        System.out.println("usage: [midi filename] [--verbose (optional)] [a list of flags and their parameters]");
        System.out.println("\tRuns all given parameter transformations in the order given.");
        System.out.println();

        System.out.println("-p (pitch bend) [default range = 2]");
        System.out.println("\tAdjusts all pitch bend events by the given default range");
        System.out.println("\tAutomatically detects and uses the range in the midi if there is one");
        System.out.println();

        System.out.println("-v (vibrato) [vibrato range = 5]");
        System.out.println("\tAdjusts all modulation events to be vibrato depth events instead");
        System.out.println("\tCreates new events adjusted to the given range value");
        System.out.println("\tFor example, modulation of 127 would create a vibrato depth of 5");
        System.out.println();

        System.out.println("-r (reverb) [reverb range = 26]");
        System.out.println("\tAdjusts all ReverbSendDepth events to the given range");
        System.out.println("\tCreates new events adjusted to the given range value");
        System.out.println("\tFor example, a value of 127 would convert to a 5");
        System.out.println();

        System.out.println("-c (clean up) [event number] [tolerance = 10]");
        System.out.println("\tCleans up all events of the given number to be within the tolerance");
        System.out.println("\tFor example, events with values 10, 12, 14, 16, 18, 20");
        System.out.println("\tWould be cleaned up to: 10, 20");
        System.out.println("\tFor pitch bends specifically, pass 'pitch-bend' for the event number");
        System.out.println();

        System.out.println("-a (add) [event number] [amount] [channel = -1]");
        System.out.println("\tAdds the given amount from all instances of the given event");
        System.out.println("\tFor pitch bends specifically, pass 'pitch-bend' for the event number");
        System.out.println("\tWill run it only for the given channel (if negative, runs for all)");
        System.out.println();

        System.out.println("-s (subtract) [event number] [amount] [channel = -1]");
        System.out.println("\tSubtracts the given amount from all instances of the given event");
        System.out.println("\tFor pitch bends specifically, pass 'pitch-bend' for the event number");
        System.out.println("\tWill run it only for the given channel (if negative, runs for all)");
        System.out.println();

        System.out.println("example: test.midi -p 3 -v -c 10 -a 77 1");
        System.out.println("\tCleans up pitch bends to a default range of 3");
        System.out.println("\tReplaces modulation events with vibrato depth, with the default range");
        System.out.println("\tCleans up panpot events to a tolerance of 10");
        System.out.println("\tAdds 1 to each vibrato depth event");
    }

    /**
     * Writes the sequence to an out file
     * @param midiName - the name of the midi
     * @param midiFile - the actual file handle of the output midi
     * @param sequence - the sequence to write out to the file
     */
    private static void writeSequenceToFile(String midiName, File midiFile, Sequence sequence) {
        String outFileName = midiName + "-out.mid";
        File file = new File(outFileName);
        try {
            int midiFileType =  MidiSystem.getMidiFileFormat(midiFile).getType();
            MidiSystem.write(sequence, midiFileType, file);

            System.out.println();
            System.out.println("File written to: " + outFileName);
            System.out.println();
        } catch (IOException | InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }

    //</editor-fold>
}

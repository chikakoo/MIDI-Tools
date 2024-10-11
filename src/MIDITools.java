import javax.sound.midi.*;
import java.io.*;
import java.util.ArrayList;
import java.text.DecimalFormat;

public class MIDITools {
    /**
     * The different transformations that can be done to a MIDI
     */
    private enum Transformation {
        Undefined,
        PitchBend,
        Vibrato,
        CleanUp,
        Add,
        Subtract
    }

    //<editor-fold desc="Constants">

    /**
     * Anvil Studio uses the "Data Slider" event for this
     * It uses the CONTROL_CHANGE message, with a Data 1 value of 6
     */
    private static final int PITCH_BEND_RANGE_DATA = 6;

    /**
     * Needed when making a new event for pitch bends
     * These events must be there after the pitch bend range
     */
    private static final int REGISTERED_PARAM_MSB = 101;
    private static final int REGISTERED_PARAM_LSB = 100;
    private static final int DATA_SLIDER_LSB = 38;
    private static final int DEFAULT_DATA_2 = 0;

    /**
     * This is what OoT sets the range to
     */
    private static final int DESIRED_PITCH_BEND_RANGE = 12;
    private static double defaultBendFactor = 6;

    /**
     * Vibrato Section
     */
    private static double defaultVibratoRange = 5;
    private static final int VIBRATO_DEPTH_EVENT = 77;
    private static final int MODULATION_EVENT = 1;
    private static final int MAX_VIBRATO_VALUE = 127;

    /**
     * Clean-up section
     */
    private static final int DEFAULT_CLEAN_UP_TOLERANCE = 10;

    //</editor-fold>

    //<editor-fold desc="Main / File Writing">

    public static void main(String[] args) {
        if (args.length < 2  || args[0].trim().isEmpty()) {
            showUsage("ERROR: The midi filename and at least one transformation is required.");
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

        int argIndex = 1;
        do {
            Transformation transformation = getTransformation(args[argIndex]);
            if (transformation == Transformation.Undefined) {
                showUsage("ERROR: Unrecognized transformation [" + args[argIndex].trim() + "]");
                System.exit(0);
            }

            String nextParameter = "";
            int eventNumber = -1;
            switch(transformation) {
                case PitchBend:
                    nextParameter = getNextParameter(args, argIndex);
                    if (!nextParameter.isEmpty()) {
                        defaultBendFactor = Double.parseDouble(getNextParameter(args, argIndex));
                        argIndex++;
                    }
                    editMidiPitchBends(sequence);
                    break;
                case Vibrato:
                    nextParameter = getNextParameter(args, argIndex);
                    if (!nextParameter.isEmpty()) {
                        defaultVibratoRange = Integer.parseInt(getNextParameter(args, argIndex));
                        argIndex++;
                    }
                    editMidiVibrato(sequence);
                    break;
                case CleanUp:
                    nextParameter = getNextParameter(args, argIndex);
                    if (!nextParameter.equals("pitch-bend")) {
                        if (!nextParameter.isEmpty()) {
                            eventNumber = Integer.parseInt(getNextParameter(args, argIndex));
                        } else {
                            showUsage("Missing required event number parameter in clean-up flag.");
                            System.exit(0);
                        }
                    }
                    argIndex++;

                    int tolerance = DEFAULT_CLEAN_UP_TOLERANCE;
                    nextParameter = getNextParameter(args, argIndex);
                    if (!nextParameter.isEmpty()) {
                        tolerance = Integer.parseInt(getNextParameter(args, argIndex));
                        argIndex++;
                    }
                    cleanUpMidiEvent(sequence, eventNumber, tolerance, eventNumber == -1);
                    break;
                case Add:
                case Subtract:
                    nextParameter = getNextParameter(args, argIndex);
                    if (!nextParameter.equals("pitch-bend")) {
                        if (!nextParameter.isEmpty()) {
                            eventNumber = Integer.parseInt(getNextParameter(args, argIndex));
                        } else {
                            showUsage("Missing required event number parameter in add/subtract flag.");
                            System.exit(0);
                        }
                    }
                    argIndex++;

                    int amount = -1;
                    nextParameter = getNextParameter(args, argIndex);
                    if (!nextParameter.isEmpty()) {
                        amount = Integer.parseInt(getNextParameter(args, argIndex));
                    } else {
                        showUsage("Missing required amount parameter in add/subtract flag.");
                        System.exit(0);
                    }
                    argIndex++;

                    // Make the amount negative if we're subtracting
                    if (transformation == Transformation.Subtract) {
                        amount *= -1;
                    }

                    addOrSubtractMidiEventValue(sequence, eventNumber, amount, eventNumber == -1);
                    break;
            }

            argIndex++;
        } while (argIndex < args.length );

        writeSequenceToFile(midiFileName, midiFile, sequence);
    }

    /**
     * Gets the next parameter from the arguments
     * - If it is a flag or the end of the array, return the empty string
     * @param args - The arguments array
     * @param currentIndex - The index of the current value we are at (the one BEFORE the one we're trying to get)
     * @return The next parameter, or the empty string if there is none
     */
    public static String getNextParameter(String[] args, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= args.length) {
            return ""; // End of the array
        }

        String nextValue = args[nextIndex].trim();
        if (nextValue.startsWith("-")) {
            return ""; // This is a flag!
        }

        return args[nextIndex];
    }

    /**
     * Gets the Transformation from the given flag
     * @param transformationString - The flag
     * @return The transformation corresponding to the given flag
     */
    private static Transformation getTransformation(String transformationString) {
        switch(transformationString.trim()) {
            case "-p":
                return Transformation.PitchBend;
            case "-v":
                return Transformation.Vibrato;
            case "-c":
                return Transformation.CleanUp;
            case "-a":
                return Transformation.Add;
            case "-s":
                return Transformation.Subtract;
            default:
                return Transformation.Undefined;
        }
    }

    /**
     * Shows the usage description
     * @param error - The reason usage is being shown
     */
    private static void showUsage(String error) {
        System.out.println(error);
        System.out.println();

        System.out.println("usage: [midi filename] [a list of flags and their parameters]");
        System.out.println("\tRuns all given parameter transformations in the order given.");
        System.out.println();

        System.out.println("-p (pitch bend) [default range = 6]");
        System.out.println("\tAdjusts all pitch bend events by the given default range");
        System.out.println("\tAutomatically detects the range in the midi if there is one");
        System.out.println();

        System.out.println("-v (vibrato) [vibrato range = 5]");
        System.out.println("\tAdjusts all modulation events to be vibrato depth events instead");
        System.out.println("\tCreates new events adjusted to the given range value");
        System.out.println("\tFor example, modulation of 127 would create a vibrato depth of 5");
        System.out.println();

        System.out.println("-c (clean up) [event number] [tolerance = 10]");
        System.out.println("\tCleans up all events of the given number to be within the tolerance");
        System.out.println("\tWill not clean up values that switch directions");
        System.out.println("\tFor example, events with values 10, 12, 14, 16, 18, 20, 18");
        System.out.println("\tWould be cleaned up to: 10, 20, 18");
        System.out.println("\tFor pitch bends specifically, pass 'pitch-bend' for the event number");
        System.out.println();

        System.out.println("-a (add) [event number] [amount]");
        System.out.println("\tAdds the given amount from all instances of the given event");
        System.out.println("\tFor pitch bends specifically, pass 'pitch-bend' for the event number");
        System.out.println();

        System.out.println("-s (subtract) [event number] [amount]");
        System.out.println("\tSubtracts the given amount from all instances of the given event");
        System.out.println("\tFor pitch bends specifically, pass 'pitch-bend' for the event number");
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
            System.out.println("File written to: " + outFileName);
        } catch (IOException | InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }

    //</editor-fold>

    //<editor-fold desc="Pitch Bends">

    /**
     * Modifies the pitch bends
     * - This does not handle pitch bend range changes in the middle of the track very well
     * - Try to scan for this and adjust the midi accordingly before running this
     * @param sequence - The sequence to modify
     */
    private static void editMidiPitchBends(Sequence sequence) {
        ArrayList<String> pitchBendRangeMessages = new ArrayList<>();
        ArrayList<String> channelsWithAdjustments = new ArrayList<>();

        boolean adjustedAnyBends = false;
        for (Track track : sequence.getTracks()) {
            // If the channel already has the desired pitch bend range, then go to the next track

            double bendFactor = tryGetNewPitchBendRangeFactor(track, pitchBendRangeMessages);
            if (bendFactor == 0) {
                // In this case, we're already using the default, so no need to continue
                continue;
            }

            // Default to this and then change it as the change events are found
            int lastChannelAdjusted = -1;
            boolean adjustedBendForThisTrack = false;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();
                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage)msg;

                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    int anvilStudioChannel = channel + 1;

                    if (command == ShortMessage.PITCH_BEND) {
                        // We're choosing not to display bend values that didn't actually change
                        if (adjustPitchBend(shortMsg, bendFactor) && !adjustedBendForThisTrack) {
                            channelsWithAdjustments.add(anvilStudioChannel + "");
                            lastChannelAdjusted = channel;
                            adjustedBendForThisTrack = true;
                            adjustedAnyBends = true;
                        }
                    }
                }
            }

            // If we have adjustments, but haven't fixed the range, we need to add the data event to set
            // the value to 12 so that it will sound correct
            if (adjustedBendForThisTrack && lastChannelAdjusted >= 0) {
                cleanUpPitchBendRangeEvents(track);
                createPitchBendRangeEvents(track, lastChannelAdjusted, pitchBendRangeMessages);
            }
        }

        if (!adjustedAnyBends) {
            printMessages(pitchBendRangeMessages);
            System.out.println("Did not find any pitch bends to adjust - exiting.");
            return;
        }

        // Print out a summary
        System.out.println();
        printMessages(pitchBendRangeMessages);

        System.out.println();
        System.out.println("Channels adjusted: " + String.join(", ", channelsWithAdjustments));

        System.out.println();
    }

    /**
     * Attempts to get the new pitch bend range factor, based on the first pitch bend range event we find
     * - If we find no event, return the default
     * - If it's the current desired range, then return 0
     * @param track - The track to check
     * @param pitchBendRangeMessages - A list of messages to display for pitch bends - adds to it if we're skipping
     * @return A double indicating the new pitch bend range factor, or 0 if we're not adjusting
     */
    private static double tryGetNewPitchBendRangeFactor(
            Track track,
            ArrayList<String> pitchBendRangeMessages) {
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage)msg;
                int anvilStudioChannel = shortMsg.getChannel() + 1;
                int command = shortMsg.getCommand();
                int data1 = shortMsg.getData1();
                int data2 = shortMsg.getData2();

                // Adjust the bend factor both for this program and in Anvil Studio
                if (command == ShortMessage.CONTROL_CHANGE && data1 == PITCH_BEND_RANGE_DATA) {
                    if (data2 == DESIRED_PITCH_BEND_RANGE) {
                        pitchBendRangeMessages.add("Channel " + anvilStudioChannel + ": Channel already has a pitch bend range of " + DESIRED_PITCH_BEND_RANGE + ".");
                        return 0;
                    } else {
                        return getNewPitchBendRangeFactor(shortMsg, pitchBendRangeMessages);
                    }
                }
            }
        }

        // If it gets here, no message was found, so adjust by the default factor
        return defaultBendFactor;
    }

    /**
     * CLeans up all pitch bend range events, as songs often have dups of these
     * @param track - The track to clean up
     */
    private static void cleanUpPitchBendRangeEvents(Track track) {
        ArrayList<MidiEvent> eventsToRemove = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage)msg;
                int command = shortMsg.getCommand();
                int data1 = shortMsg.getData1();

                // Adjust the bend factor both for this program and in Anvil Studio
                if ((command == ShortMessage.CONTROL_CHANGE && data1 == PITCH_BEND_RANGE_DATA) ||
                        (command == ShortMessage.CONTROL_CHANGE && data1 == REGISTERED_PARAM_MSB) ||
                        (command == ShortMessage.CONTROL_CHANGE && data1 == REGISTERED_PARAM_LSB) ||
                        (command == ShortMessage.CONTROL_CHANGE && data1 == DATA_SLIDER_LSB)) {
                    eventsToRemove.add(e);
                }
            }
        }

        deleteEventsFromTrack(track, eventsToRemove);
    }

    /**
     * Creates a new set of pitch bend range events
     * @param track - The track to create the events for
     * @param lastChannelAdjusted - the last channel adjusted
     * @param pitchBendRangeMessages - the pitch bend range messages to add to
     */
    private static void createPitchBendRangeEvents(
            Track track,
            int lastChannelAdjusted,
            ArrayList<String> pitchBendRangeMessages) {
        ShortMessage pitchBendRangeMessage = new ShortMessage();
        ShortMessage registeredParamMSB = new ShortMessage();
        ShortMessage registeredParamLSB = new ShortMessage();
        ShortMessage dataSliderLSBMessage = new ShortMessage();

        setShortMessage(pitchBendRangeMessage, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, PITCH_BEND_RANGE_DATA, DESIRED_PITCH_BEND_RANGE);
        setShortMessage(registeredParamMSB, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, REGISTERED_PARAM_MSB, DEFAULT_DATA_2);
        setShortMessage(registeredParamLSB, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, REGISTERED_PARAM_LSB, DEFAULT_DATA_2);
        setShortMessage(dataSliderLSBMessage, ShortMessage.CONTROL_CHANGE, lastChannelAdjusted, DATA_SLIDER_LSB, DEFAULT_DATA_2);

        track.add(new MidiEvent(registeredParamMSB, 0));
        track.add(new MidiEvent(registeredParamLSB, 0));
        track.add(new MidiEvent(pitchBendRangeMessage, 0));
        track.add(new MidiEvent(dataSliderLSBMessage, 0));

        pitchBendRangeMessages.add("Channel " + (lastChannelAdjusted + 1) + ": Created new pitch bend range with a value of 12");
    }

    /**
     * Gets what the new pitch bend range factor should be
     * @param shortMsg - the message to base the range factor off of
     * @param pitchBendRangeMessages - an array of messages to potentially add to
     * @return The bend factor to use
     */
    private static double getNewPitchBendRangeFactor(ShortMessage shortMsg, ArrayList<String> pitchBendRangeMessages) {
        int channel = shortMsg.getChannel();
        int anvilStudioChannel = channel + 1;
        int oldPitchBendRange = shortMsg.getData2();

        // Some MIDIs have a value greater than 12, which is invalid
        // Same story for those with a value less than 1
        // Anvil Studio treats it as a 2, so we will do the same
        double bendFactor = (oldPitchBendRange > DESIRED_PITCH_BEND_RANGE) && (oldPitchBendRange > 0)
            ? defaultBendFactor
            : (double)DESIRED_PITCH_BEND_RANGE / oldPitchBendRange;

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        pitchBendRangeMessages.add("Channel " + anvilStudioChannel + ": Adjusted pitch bend range from " + oldPitchBendRange + " to " + DESIRED_PITCH_BEND_RANGE + " which is a factor of " + df.format(bendFactor));

        return bendFactor;
    }

    /**
     * Adjusts the pitch bend value on the message
     * @param shortMsg - the message to adjust
     * @param bendFactor - the amount to adjust the bend by - this is the desired value divided by the current one
     *                   Normally 12 divided by whatever it's set in Anvil Studio at the moment
     * @return A boolean indicating whether we actually adjusted the value
     */
    private static boolean adjustPitchBend(ShortMessage shortMsg, double bendFactor) {
        final int BASE_VALUE = 8192;

        int channel = shortMsg.getChannel();
        int value = getPitchBendValue(shortMsg.getData1(), shortMsg.getData2());

        int valueToAdjust = value - BASE_VALUE; // Get the difference of 8192 off the value
        int adjustedValue = (int)(valueToAdjust / bendFactor); // Adjust that difference

        int newValue = adjustedValue + BASE_VALUE; // Add the adjusted value back to the base
        int newData2 = newValue / 128;
        int newData1 = newValue % 128;

        setShortMessage(shortMsg, ShortMessage.PITCH_BEND, channel, newData1, newData2);

        int realNewValue = getPitchBendValue(newData1, newData2);
        if (realNewValue != BASE_VALUE) {
            System.out.println("Channel " + (channel + 1) + ": Adjusting pitch bend value " + value + " to be " + realNewValue);
            return true;
        }

        return false;
    }

    /**
     * Gets the pitch bend value from the two data points
     * @param data1 - The precise value - simply adds to the pitch bend value
     * @param data2 - Each value of this adds 128 to the pitch bend value
     * @return The pitch bend value as an integer
     */
    private static int getPitchBendValue(int data1, int data2) {
        return data1 + (data2 * 128);
    }

    //</editor-fold>

    //<editor-fold desc="Vibrato">

    /**
     * Converts modulation events into vibrato depth events (that's what seq 64 uses)
     * - Will convert it to the closest value to the defaultVibratoRange (rounded up)
     * - For example, a range of 5 and a modulation of 10 will give a vibrato depth of 1
     * @param sequence - The sequence to modify
     */
    private static void editMidiVibrato(Sequence sequence) {
        ArrayList<String> vibratoMessages = new ArrayList<>();
        boolean addedAnyVibratos = false;
        for (Track track : sequence.getTracks()) {
            if (!checkNeedToAddVibratoEventsAndDeleteIfSo(track)) {
                continue;
            }

            boolean addedVibratoEventAtBeginning = false;
            int lastVibratoAdded = -1;
            int lastChannel = -1;
            for (int i = 0; i < track.size(); i++) {
                boolean isLastItem = i + 1 >= track.size();
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();

                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    lastChannel = channel;

                    if (command == ShortMessage.CONTROL_CHANGE) {
                        int data1 = shortMsg.getData1();
                        int data2 = shortMsg.getData2();

                        if (data1 == MODULATION_EVENT) {
                            // Set the flag if we're adding a vibrato at the start so that we know
                            // NOT to insert a vibrato event of 0 later on
                            if (e.getTick() == 0) {
                                addedVibratoEventAtBeginning = true;
                            }

                            // Add a new event if needed
                            int vibratoDepth = (int)Math.ceil(defaultVibratoRange * ((double)data2 / (double)MAX_VIBRATO_VALUE));
                            if (lastVibratoAdded != vibratoDepth) {
                                // Only list the message once; do so before the first vibrato is added
                                if (lastVibratoAdded == -1) {
                                    vibratoMessages.add("Channel " + (channel + 1) + ": added vibrato!");
                                }

                                addedAnyVibratos = true;
                                lastVibratoAdded = vibratoDepth;
                                addVibratoToTrack(track, channel, vibratoDepth, e.getTick());
                            }
                        }
                    }
                }

                // If we're on the last item, insert a beginning vibrato message if we need it
                if (!addedVibratoEventAtBeginning && isLastItem && lastChannel != -1) {
                    addVibratoToTrack(track, lastChannel, 0, 0);
                    break; // Adding a new vibrato will increase the list, and we'll loop forever!
                }
            }
        }

        if (addedAnyVibratos) {
            printMessages(vibratoMessages);
        }
    }

    /**
     * Checks whether we will be adding vibrato events and deletes any existing ones if we will be
     * - If there are no modulation events, no need to edit anything
     * - If there ARE vibrato events, we don't want to mess things up, so leave a message and also don't add anything
     * @param track - the track to check
     * @return
     */
    private static boolean checkNeedToAddVibratoEventsAndDeleteIfSo(Track track) {
        ArrayList<MidiEvent> vibratoEventsToDelete = new ArrayList<>();

        boolean foundModulationEvent = false;
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage) msg;
                int command = shortMsg.getCommand();

                if (command == ShortMessage.CONTROL_CHANGE) {
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();

                    if (data1 == VIBRATO_DEPTH_EVENT) {
                        vibratoEventsToDelete.add(e);
                    } else if (data1 == MODULATION_EVENT && data2 > 0) {
                        foundModulationEvent = true;
                    }
                }
            }
        }

        if (foundModulationEvent) {
            // Only delete these if we're adding new ones
            deleteEventsFromTrack(track, vibratoEventsToDelete);
        }

        return foundModulationEvent;
    }

    /**
     * Adds a vibrato event to the midi
     * @param track - the track to add to
     * @param channel - the channel to add to
     * @param vibratoDepth - the amount to set the vibrato to
     * @param tick - when to set it
     */
    private static void addVibratoToTrack(Track track, int channel, int vibratoDepth, long tick) {
        ShortMessage vibratoDepthMessage = new ShortMessage();
        setShortMessage(vibratoDepthMessage, ShortMessage.CONTROL_CHANGE, channel, VIBRATO_DEPTH_EVENT, vibratoDepth);
        track.add(new MidiEvent(vibratoDepthMessage, tick));
    }

    //</editor-fold>

    //<editor-fold desc="Clean Up">

    private static void cleanUpMidiEvent(
        Sequence sequence,
        int eventNumber,
        int tolerance,
        boolean cleanUpPitchBend) {
        for (Track track : sequence.getTracks()) {
            ArrayList<MidiEvent> eventsToDelete = new ArrayList<>();
            int lastBaseValue = -1;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();
                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();
                    int value = -1;

                    if (cleanUpPitchBend) {
                        if (command == ShortMessage.PITCH_BEND) {
                            value = getPitchBendValue(data1, data2);
                        } else {
                            // We're only looking for pitch bend commands here
                            continue;
                        }
                    } else if (command == ShortMessage.CONTROL_CHANGE && data1 == eventNumber) {
                        value = data2;
                    } else {
                        // This is not the event we're looking for
                        continue;
                    }

                    // We never want two events in a row with the same value
                    // It also says nothing about the current direction, so just continue
                    if (value == lastBaseValue) {
                        eventsToDelete.add(e);
                        continue;
                    }

                    // The very first time this runs - just set the base value
                    if (lastBaseValue == -1) {
                        lastBaseValue = value;
                        continue;
                    }

                    // Next value is greater than the base value, but not within tolerance
                    if (value > lastBaseValue &&
                        lastBaseValue + tolerance > value) {
                        eventsToDelete.add(e);
                    }

                    // Next value is smaller than the base value, but not within tolerance
                    else if (value < lastBaseValue &&
                        lastBaseValue - tolerance < value) {
                        eventsToDelete.add(e);
                    }

                    // All other cases, we've kept the event, so we should update the base value
                    else {
                        lastBaseValue = value;
                    }
                }
            }

            deleteEventsFromTrack(track, eventsToDelete);
        }
    }

    //</editor-fold>

    //<editor-fold desc="Add/Subtract Event Values">

    /**
     * Adds or subtracts the given amount from the given midi event
     * @param sequence - The sequence to modify
     * @param eventNumber - The event number (if not modifying pitch bends)
     * @param amount - The amount to modify by - negative number to subtract
     * @param modifyPitchBendEvent - Whether this is modifying a pitch bend event
     */
    private static void addOrSubtractMidiEventValue(
        Sequence sequence,
        int eventNumber,
        int amount,
        boolean modifyPitchBendEvent) {
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();
                int value = -1;

                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();

                    if (modifyPitchBendEvent) {
                        if (command == ShortMessage.PITCH_BEND) {
                            value = getPitchBendValue(data1, data2);
                            value += amount;

                            int newData1 = value % 128;
                            int newData2 = value / 128;
                            setShortMessage(shortMsg, ShortMessage.PITCH_BEND, channel, newData1, newData2);
                        }
                    } else if (command == ShortMessage.CONTROL_CHANGE && data1 == eventNumber) {
                        value = data2 + amount;
                        setShortMessage(shortMsg, ShortMessage.CONTROL_CHANGE, channel, eventNumber, value);
                    }
                }
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="Common Functions">

    /**
     * Prints the given list of messages
     * @param messages - the messages to print
     */
    private static void printMessages(ArrayList<String> messages) {
        for (String pitchBendRangeMessage : messages) {
            System.out.println(pitchBendRangeMessage);
        }
    }

    /**
     * Sets the short message - helper function so that we don't need to use try/catches everywhere
     */
    private static void setShortMessage(ShortMessage shortMsg, int command, int channel, int data1, int data2) {
        try {
            shortMsg.setMessage(command, channel, data1, data2);
        } catch(InvalidMidiDataException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Deletes the given array of events from the track
     * NEVER call this in the middle of traversing the events in a loop!
     * @param track - The track
     * @param eventsToRemove - The events to remove
     */
    private static void deleteEventsFromTrack(Track track, ArrayList<MidiEvent> eventsToRemove) {
        for(MidiEvent e : eventsToRemove) {
            track.remove(e);
        }
    }

    //</editor-fold desc="Common Functions">
}

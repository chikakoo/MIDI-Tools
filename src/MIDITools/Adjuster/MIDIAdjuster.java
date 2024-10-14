package MIDITools.Adjuster;

import MIDITools.MIDITools;

import javax.sound.midi.*;
import java.util.ArrayList;

public abstract class MIDIAdjuster {
    /**
     * What to use as the event number parameter for pitch bends
     */
    public static final String PITCH_BEND_ARG = "pitch-bend";

    /**
     * Executes the transformation and returns the index of the next one (or the end of the list)
     * @param args - All the args passed via command line
     * @param currentIndex The index to start looking (the one at the current transformation flag)
     * @param sequence The sequence we are modifying
     * @return The index after the last parameter (or -1 if there was a problem)
     */
    public abstract int execute(String[] args, int currentIndex, Sequence sequence);

    /**
     * Gets all the arguments from the current index and returns them in a list
     * - Flags are currently found by checking whether the string stars with a hyphen (-)
     * @param args - All the args passed via command line
     * @param currentIndex - The index of the flag to get args for
     * @return All the args until either the end of the list, or the next flag
     */
    protected ArrayList<String> getAllArgs(String[] args, int currentIndex) {
        ArrayList<String> transformationArgs = new ArrayList<>();

        // Note that we start looking for args one AFTER where we begin!
        for(int i = currentIndex + 1; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("-")) {
                break;
            }
            transformationArgs.add(arg);
        }
        return transformationArgs;
    }

    /**
     * Prints the given list of messages
     * @param messages - the messages to print
     */
    protected static void printMessages(ArrayList<String> messages) {
        for (String pitchBendRangeMessage : messages) {
            System.out.println(pitchBendRangeMessage);
        }
    }

    /**
     * Sets the short message - helper function so that we don't need to use try/catches everywhere
     */
    protected static void setShortMessage(ShortMessage shortMsg, int command, int channel, int data1, int data2) {
        try {
            shortMsg.setMessage(command, channel, data1, data2);
        } catch(InvalidMidiDataException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Adds a new event short message to the midi
     * @param track - The track to add to
     * @param channel - The channel to add to
     * @param eventNumber - The event number to add
     * @param eventValue - The value to set the event to
     * @param tick - When to set it
     * @param eventDisplayName - The name of the event to show in the verbose log
     */
    protected static void addNewShortMessage(
            Track track,
            int channel,
            int eventNumber,
            int eventValue,
            long tick,
            String eventDisplayName) {
        ShortMessage vibratoDepthMessage = new ShortMessage();
        setShortMessage(vibratoDepthMessage, ShortMessage.CONTROL_CHANGE, channel, eventNumber, eventValue);
        track.add(new MidiEvent(vibratoDepthMessage, tick));

        verboseLog("Added " + eventDisplayName + " of " + eventValue + " at tick " + tick, channel);
    }

    /**
     * Deletes the given array of events from the track
     * NEVER call this in the middle of traversing the events in a loop!
     * @param track - The track
     * @param eventsToRemove - The events to remove
     */
    protected static void deleteEventsFromTrack(Track track, ArrayList<MidiEvent> eventsToRemove) {
        for(MidiEvent e : eventsToRemove) {
            track.remove(e);
        }
    }

    /**
     * Displays a message listing out what channels were modified, prefixed by the given message
     * @param channelsModified - The list of channels modified
     * @param messagePrefix - The message to put in front of the list of channels
     */
    protected static void showChannelsModifiedMessage(ArrayList<String> channelsModified, String messagePrefix) {
        if (!channelsModified.isEmpty()) {
            String channelString = String.join(", ", channelsModified);
            System.out.println(messagePrefix + ": " + channelString);
        }
    }

    /**
     * Logs a message if verbose logging is enabled
     * @param message - The message to log
     * @param channel - The channel to include in the message
     */
    protected static void verboseLog(String message, int channel) {
        if (MIDITools.verboseLogging) {
            System.out.println("Channel " + (channel + 1) + ": " + message);
        }
    }
}

package MIDITools.Adjuster;

import MIDITools.MIDITools;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.util.ArrayList;

public class MIDIAdjuster {
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

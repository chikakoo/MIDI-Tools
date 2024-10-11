package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

public class EventReplacer extends MIDIAdjuster {
    /**
     * All supported events here have a max of 127
     */
    private static final int MAX_EVENT_VALUE = 127;

    /**
     * Takes in an event of one type and replaces instances of this value with events of a second type
     * @param sequence - The sequence to modify
     * @param oldEventNumber - The event to replace
     * @param newEventNumber - The event to replace the given event with
     * @param divisionAmount - The amount to divide the old event by when creating the new event
     * @param eventDisplayName - The display name of the event, for logging
     */
    public static void replaceMidiEvents(
            Sequence sequence,
            int oldEventNumber,
            int newEventNumber,
            double divisionAmount,
            String eventDisplayName) {
        ArrayList<String> channelsAffected = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            if (!checkNeedToAddEventsAndDeleteIfSo(track, oldEventNumber, newEventNumber)) {
                continue;
            }

            boolean addedNewEventAtBeginning = false;
            int lastNewEventValue = -1;
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

                        if (data1 == oldEventNumber) {
                            // Set the flag if we're adding a new event at the start so that we know
                            // NOT to insert one with a value of 0 later on
                            if (e.getTick() == 0) {
                                addedNewEventAtBeginning = true;
                            }

                            // Add a new event if needed, divided by the amount given
                            int newEventValue = (int)Math.ceil(divisionAmount * ((double)data2 / (double)MAX_EVENT_VALUE));
                            if (lastNewEventValue != newEventValue) {
                                // Only list the message once; do so before the first new event is added
                                if (lastNewEventValue == -1) {
                                    channelsAffected.add(String.valueOf(channel + 1));
                                }

                                lastNewEventValue = newEventValue;
                                addNewShortMessage(
                                    track,
                                    channel,
                                    newEventNumber,
                                    newEventValue,
                                    e.getTick(),
                                    eventDisplayName);
                            }
                        }
                    }
                }

                // If we're on the last item, insert a beginning event with the value 0 if needed
                if (!addedNewEventAtBeginning && isLastItem && lastChannel != -1) {
                    addNewShortMessage(
                        track,
                        lastChannel,
                        newEventNumber,
                        0,
                        0,
                        eventDisplayName);
                    break; // Adding a new event will increase the list size, and we'll loop forever!
                }
            }
        }

        if (!channelsAffected.isEmpty()) {
            showChannelsModifiedMessage(channelsAffected, eventDisplayName + " added to channels");
            System.out.println();
        }
    }

    /**
     * Checks whether we will be adding events and deletes any existing ones if we will be
     * - If there are none of the old event, no need to edit anything
     * - If there are any new events, delete them; we don't want to end up with anything unexpected
     * @param track - The track to check
     * @param oldEventNumber - The event number to be replaced with the new
     * @param newEventNumber - The event number that will replace the old
     * @return True if we need to add new events; false otherwise
     */
    private static boolean checkNeedToAddEventsAndDeleteIfSo(
            Track track,
            int oldEventNumber,
            int newEventNumber) {
        ArrayList<MidiEvent> eventsToDelete = new ArrayList<>();

        boolean foundEventToReplace = false;
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage) msg;
                int command = shortMsg.getCommand();

                if (command == ShortMessage.CONTROL_CHANGE) {
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();

                    // Delete any events of the new type that already exist
                    if (data1 == newEventNumber) {
                        eventsToDelete.add(e);
                    }

                    // Mark that there are, in fact, events to replace
                    else if (data1 == oldEventNumber && data2 > 0) {
                        foundEventToReplace = true;
                    }
                }
            }
        }

        if (foundEventToReplace) {
            // Only delete these if we're adding new ones
            deleteEventsFromTrack(track, eventsToDelete);
        }

        return foundEventToReplace;
    }
}

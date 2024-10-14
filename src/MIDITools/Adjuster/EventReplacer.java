package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

public abstract class EventReplacer extends MIDIAdjuster {
    /**
     * All supported events here have a max of 127
     */
    private static final int MAX_EVENT_VALUE = 127;

    /**
     * A list of the MIDI events to delete at the end
     * We need to do this at the end in case the old and new events are the same
     */
    private final ArrayList<MidiEvent> eventsToDelete = new ArrayList<>();

    /**
     * Takes in an event of one type and replaces instances of this value with events of a second type
     * @param sequence - The sequence to modify
     * @param oldEventNumber - The event to replace
     * @param newEventNumber - The event to replace the given event with
     * @param divisionAmount - The amount to divide the old event by when creating the new event
     * @param eventDisplayName - The display name of the event, for logging
     */
    protected void replaceMidiEvents(
            Sequence sequence,
            int oldEventNumber,
            int newEventNumber,
            double divisionAmount,
            String eventDisplayName) {
        ArrayList<NewMIDIEvent> eventsToAdd = new ArrayList<>();
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
                                eventsToAdd.add(
                                        new NewMIDIEvent(
                                                track,
                                                channel,
                                                newEventNumber,
                                                newEventValue,
                                                e.getTick(),
                                                eventDisplayName)
                                    );
                            }
                        }
                    }
                }

                // If we're on the last item, insert a beginning event with the value 0 if needed
                if (!addedNewEventAtBeginning && isLastItem && lastChannel != -1) {
                    eventsToAdd.add(
                            new NewMIDIEvent(
                                    track,
                                    lastChannel,
                                    newEventNumber,
                                    0,
                                    0,
                                    eventDisplayName)
                        );
                }
            }

            // We've processed this track and know what events to add,
            // so we're good to delete the old ones now
            deleteEventsFromTrack(track, eventsToDelete);
        }

        // Actually add the events now
        for(NewMIDIEvent eventToAdd : eventsToAdd) {
            eventToAdd.addToMIDI();
        }

        if (!channelsAffected.isEmpty()) {
            showChannelsModifiedMessage(channelsAffected, eventDisplayName + " added to channels");
            System.out.println();
        }
    }

    /**
     * Checks whether we will be adding events and marks any existing ones as deleted if necessary
     * Supports passing the same event in old/new to modify its value
     * - If there are none of the old event, no need to edit anything
     * - If there are any new events, delete them; we don't want to end up with anything unexpected
     * @param track - The track to check
     * @param oldEventNumber - The event number to be replaced with the new
     * @param newEventNumber - The event number that will replace the old
     * @return True if we need to add new events; false otherwise
     */
    private boolean checkNeedToAddEventsAndDeleteIfSo(
            Track track,
            int oldEventNumber,
            int newEventNumber) {
        eventsToDelete.clear();
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
                    // Note that this isn't an else because we support passing in the same event type twice
                    if (data1 == oldEventNumber && data2 > 0) {
                        foundEventToReplace = true;
                    }
                }
            }
        }

        return foundEventToReplace;
    }

    /**
     * Represents a new MIDI Event to insert
     * This is used when we're going through a loop and adding events, but we don't want the new
     * events to affect what values we're looping through
     */
    private static class NewMIDIEvent {
        public Track track;
        public int channel;
        public int eventNumber;
        public int eventValue;
        public long tick;
        public String eventDisplayName;

        public NewMIDIEvent(
                Track track,
                int channel,
                int eventNumber,
                int eventValue,
                long tick,
                String eventDisplayName) {
            this.track = track;
            this.channel = channel;
            this.eventNumber = eventNumber;
            this.eventValue = eventValue;
            this.tick = tick;
            this.eventDisplayName = eventDisplayName;
        }

        /**
         * Adds this MIDI event to the current track
         */
        public void addToMIDI() {
            addNewShortMessage(
                track,
                channel,
                eventNumber,
                eventValue,
                tick,
                eventDisplayName);
        }
    }
}

package base.tasks;

import base.Keyable;
import base.ProtocolDefinitions;
import base.ThreadManager;
import base.channels.ChannelHandler;
import base.messages.CommonMessage;
import base.messages.MessageFactory;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

public abstract class Task implements Keyable {
    private byte[] message;
    protected final String file_id;
    protected int current_attempt;
    private ScheduledFuture next_action;

    public Task(String file_name) {
        this.file_id = MessageFactory.filenameEncode(file_name);
        this.current_attempt = 0;
    }

    protected final void prepareMessage() {
        this.message = this.createMessage();
    }

    public abstract void notify(CommonMessage msg);

    protected final void startCommuncation() {
        // Kickstarting the channels "loop"
        ThreadManager.getInstance().executeLater(this::communicate);
    }

    protected abstract byte[] createMessage();

    protected void handleMaxRetriesReached() {
        this.unregister();
    }

    public final void communicate() {
        if (this.current_attempt >= ProtocolDefinitions.MESSAGE_DELAYS.length) {
            this.handleMaxRetriesReached();
            return;
        }

        try {
            printSendingMessage();
            getChannel().broadcast(this.message);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.next_action = ThreadManager.getInstance().executeLater(() -> {
            this.current_attempt++;
            this.communicate();
        }, ProtocolDefinitions.MESSAGE_DELAYS[this.current_attempt]);
    }

    protected abstract ChannelHandler getChannel();

    protected final void cancelCommunication() {
        this.next_action.cancel(true);
    }

    protected abstract void printSendingMessage();

    protected final void unregister() {
        TaskManager.getInstance().unregisterTask(this);
    }
}

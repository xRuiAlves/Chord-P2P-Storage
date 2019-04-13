package base.protocol.subprotocols;

import base.protocol.task.*;
import base.storage.requested.RequestedBackupFileChunk;
import base.storage.requested.RequestedBackupsState;

import java.util.concurrent.ConcurrentHashMap;

public class BackupSubprotocol implements ITaskObserver {
    private static final int MAX_RUNNING_PUTCHUNK_TASKS = 10;

    private final String file_id;
    private final int replication_degree;
    private final byte[][] chunks_data;
    private final ConcurrentHashMap<Task, Boolean> running_tasks = new ConcurrentHashMap<>();
    private final int last_chunk_no;
    private final boolean is_enhanced_version;
    // Access to this must be synchronized
    private int last_running_chunk_no;

    public BackupSubprotocol(String file_id, int replication_degree, byte[][] chunks_data, boolean is_enhanced_version) {
        this.file_id = file_id;
        this.replication_degree = replication_degree;
        this.chunks_data = chunks_data;
        this.last_chunk_no = chunks_data.length;
        this.is_enhanced_version = is_enhanced_version;
        this.last_running_chunk_no = 0;

        launchInitialTasks();
    }

    private synchronized int getLastRunningChunkNo() {
        return this.last_running_chunk_no;
    }

    private synchronized void incrementLastRunningChunkNo() {
        this.last_running_chunk_no++;
    }

    private void stopAllTasks() {
        // TODO
        // Iterate over the hashmap keys and unregister all of the tasks. Print "not success"
        System.out.println("Stopping all of the tasks because one was not successful");
        this.running_tasks.keySet().forEach(Task::unregister);
        this.running_tasks.clear();

        System.out.println("All tasks stopped.");
        System.out.printf("Backup of file with id %s unsuccessful. Running tasks terminated and process aborted.\n", this.file_id);
        // TODO Suicide object

        // TODO Launch delete subprotocol for this file_id
    }

    @Override
    public void notifyEnd(boolean success, int task_id) {
        if (!success) {
            System.out.printf("Task for chunk %d was not successful.\n", task_id);
        } else {
            this.launchNextTask();
        }
    }

    private void launchInitialTasks() {
        while (this.running_tasks.size() < MAX_RUNNING_PUTCHUNK_TASKS && this.getLastRunningChunkNo() < this.last_chunk_no) {
            this.launchNextTask();
        }
    }

    private synchronized void launchNextTask() {
        if (this.running_tasks.size() >= MAX_RUNNING_PUTCHUNK_TASKS || this.getLastRunningChunkNo() >= this.last_chunk_no) {
            // Preventing launching more tasks than desired
            return;
        }

        final int last_running_chunk_no = this.getLastRunningChunkNo();

        System.out.printf("Launching task for chunk_no %d. #Running tasks: %d\n", last_running_chunk_no, this.running_tasks.size());

        // TODO implement notifying in the tasks (maybe method in Task? or just have a NullObserver)
        if (this.is_enhanced_version) {
            Task t = new EnhancedPutchunkTask(file_id, last_running_chunk_no, replication_degree, chunks_data[last_running_chunk_no]);
            t.observe(this);
            TaskManager.getInstance().registerTask(t);
            RequestedBackupsState.getInstance().getRequestedFileBackupInfo(file_id).registerChunk(new RequestedBackupFileChunk(file_id, last_running_chunk_no, replication_degree));
        } else {
            Task t = new PutchunkTask(file_id, last_running_chunk_no, replication_degree, chunks_data[last_running_chunk_no]);
            t.observe(this);
            TaskManager.getInstance().registerTask(t);
            RequestedBackupsState.getInstance().getRequestedFileBackupInfo(file_id).registerChunk(new RequestedBackupFileChunk(file_id, last_running_chunk_no, replication_degree));
        }

        this.incrementLastRunningChunkNo();
    }
}
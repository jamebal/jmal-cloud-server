package com.jmal.clouddisk.lucene;

import cn.hutool.crypto.SecureUtil;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
@RequiredArgsConstructor
public class TaskProgressService {

    private final CommonFileService commonFileService;

    private final UserServiceImpl userService;

    private final static Map<String, TaskProgress> TASK_PROGRESS_MAP = new ConcurrentSkipListMap<>();

    private final static String MSG_TASK_PROGRESS = "taskProgress";

    private String defaultUsername;

    public List<TaskProgress> getTaskProgressList() {
        return List.copyOf(TASK_PROGRESS_MAP.values());
    }

    public void addTaskProgress(File file, TaskType taskType, String progress) {
        if (defaultUsername == null) {
            defaultUsername = userService.getCreatorUsername();
        }
        String taskId = getTaskId(file);
        TaskProgress taskProgress;
        if (checkTaskProgress(taskId)) {
            taskProgress = getTaskProgress(taskId);
            taskProgress.setProgress(progress);
        } else {
            String username = commonFileService.getUsernameByAbsolutePath(file.toPath());
            if (username == null) {
                return;
            }
            taskProgress = new TaskProgress(taskId, username, taskType, file.getName(), progress);
        }
        addTaskProgress(taskProgress);
    }

    public void removeTaskProgress(File file) {
        String taskId = getTaskId(file);
        removeTaskProgress(taskId);
    }

    private static String getTaskId(File file) {
        return SecureUtil.md5(file.getAbsoluteFile().toString());
    }

    private void removeTaskProgress(String taskId) {
        if (checkTaskProgress(taskId)) {
            TaskProgress taskProgress = getTaskProgress(taskId);
            taskProgress.setProgress(null);
            pushMessage(taskProgress);
            TASK_PROGRESS_MAP.remove(taskId);
        }
    }

    private void addTaskProgress(TaskProgress taskProgress) {
        TASK_PROGRESS_MAP.put(taskProgress.getTaskId(), taskProgress);
        pushMessage(taskProgress);
    }

    private boolean checkTaskProgress(String taskId) {
        return TASK_PROGRESS_MAP.containsKey(taskId);
    }

    private TaskProgress getTaskProgress(String taskId) {
        return TASK_PROGRESS_MAP.get(taskId);
    }

    private void pushMessage(TaskProgress taskProgress) {
        if (!taskProgress.getUsername().equals(defaultUsername)) {
            commonFileService.pushMessage(defaultUsername, taskProgress, MSG_TASK_PROGRESS);
        }
        commonFileService.pushMessage(taskProgress.getUsername(), taskProgress, MSG_TASK_PROGRESS);
    }

}

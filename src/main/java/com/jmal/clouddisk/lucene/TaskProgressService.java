package com.jmal.clouddisk.lucene;

import cn.hutool.crypto.SecureUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskProgressService {

    private final CommonFileService commonFileService;

    private final FileProperties fileProperties;

    private final UserServiceImpl userService;

    private final static Map<String, TaskProgress> TASK_PROGRESS_MAP = new ConcurrentSkipListMap<>();

    private final static String MSG_TASK_PROGRESS = "taskProgress";

    private final static String MSG_TRANSCODE_STATUS = "transcodeStatus";

    private String defaultUsername;

    public List<TaskProgress> getTaskProgressList() {
        return List.copyOf(TASK_PROGRESS_MAP.values());
    }

    /**
     * 更新转码状态
     * @param transcodeStatus 转码状态
     */
    public void pushTranscodeStatus(Map<String, Integer> transcodeStatus) {
        commonFileService.pushMessage(getDefaultUsername(), transcodeStatus, MSG_TRANSCODE_STATUS);
    }

    /**
     * 添加任务进度
     * @param file 文件
     * @param taskType 任务类型
     * @param progress 进度
     */
    public void addTaskProgress(File file, TaskType taskType, String progress) {
        getDefaultUsername();
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
            taskProgress.setPath(commonFileService.getRelativePath(username, file.getAbsolutePath(), file.getName()));
        }
        addTaskProgress(taskProgress);
    }

    /**
     * 移除任务进度
     * @param file 文件
     */
    public void removeTaskProgress(File file) {
        String taskId = getTaskId(file);
        removeTaskProgress(taskId);
    }

    private String getDefaultUsername() {
        if (defaultUsername == null) {
            defaultUsername = userService.getCreatorUsername();
        }
        return defaultUsername;
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

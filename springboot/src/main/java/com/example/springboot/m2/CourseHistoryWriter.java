package com.example.springboot.m2;

import com.example.springboot.m2.mapper.CourseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 只负责在短事务中保存本次兴趣画像和推荐历史，不进行检索或调用大模型。
 *
 * <p>单独放在一个 Spring Bean 中，让 CourseService 通过事务代理调用；这样网络等待不占用 写事务，任一保存步骤失败也能整体回滚。设计原因见
 * CODE_GUIDE.md#coursehistorywriter。
 */
@Service
public class CourseHistoryWriter {
    private final CourseMapper mapper;
    private final ObjectMapper json;

    public CourseHistoryWriter(CourseMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(String username, String interests, String goals, Object result)
            throws Exception {
        // 两次写入属于同一事务：任一步失败时，画像和历史一起回滚。
        mapper.saveProfile(username, interests, goals);
        mapper.addHistory(username, interests + " " + goals, json.writeValueAsString(result));
    }
}

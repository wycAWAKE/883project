package com.example.springboot.administration;
import com.example.springboot.administration.Models.*;
import com.example.springboot.administration.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import static com.example.springboot.administration.Api.require;
@Service
public class NotificationService {
 private final NoticeMapper notices;
 public NotificationService(NoticeMapper notices){this.notices=notices;}
 public Api.Page<Notice> list(User user,Boolean unread,int page,int size) {
  require(page>=1 && page<=100000 && size>=1 && size<=50,400,"Invalid page number or page size.");
  QueryWrapper<Notice> q=new QueryWrapper<Notice>().eq("user_id",user.id());if(Boolean.TRUE.equals(unread))q.eq("is_read",false);
  long total=notices.selectCount(q);q.orderByDesc("created_at","id").last("LIMIT "+size+" OFFSET "+((page-1)*size));
  return new Api.Page<>(notices.selectList(q),total,page,size);
 }
 public long unread(User user){return notices.selectCount(new QueryWrapper<Notice>().eq("user_id",user.id()).eq("is_read",false));}
 public void read(User user,String id) {
  require(notices.selectCount(new QueryWrapper<Notice>().eq("id",id).eq("user_id",user.id()))==1,404,"Notification not found.");
  notices.update(null,new UpdateWrapper<Notice>().eq("id",id).eq("user_id",user.id()).set("is_read",true));
 }
 public void readAll(User user){notices.update(null,new UpdateWrapper<Notice>().eq("user_id",user.id()).eq("is_read",false).set("is_read",true));}
}

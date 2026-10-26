// Translate system-owned labels only. Never transform API records or submitted values.
const labels = {
  男: 'Male', 女: 'Female',
  待处理: 'Pending', 未处理: 'Pending', 未完成: 'Incomplete', 完成: 'Completed',
  通过: 'Approved', 驳回: 'Rejected', 充值: 'Top-up', 扣费: 'Deduction',
  优秀: 'Excellent', 良好: 'Good', 合格: 'Pass', 不合格: 'Fail', 待改进: 'Needs Improvement',
  首页: 'Home', 用户管理: 'Users', 学生信息: 'Students', 宿管信息: 'Dormitory Staff',
  宿舍管理: 'Dormitories', 楼宇信息: 'Buildings', 房间信息: 'Rooms',
  信息管理: 'Information', 公告信息: 'Announcements', 报修信息: 'Maintenance Requests',
  申请管理: 'Applications', 调宿申请: 'Room Transfers', 访客管理: 'Visitors',
  我的宿舍: 'My Room', 申请调宿: 'Request Room Transfer', 报修申请: 'Request Maintenance',
  水电费管理: 'Utilities', 水费管理: 'Water Charges', 电费管理: 'Electricity Charges',
  卫生检查: 'Room Inspections', 权限说明: 'Permissions', 个人信息: 'My Profile',
  系统管理员: 'Administrator', 宿舍管理员: 'Dormitory Staff', 学生: 'Student',
  拥有系统全部管理菜单和账号维护权限: 'Access to all management modules and account maintenance.',
  '负责学生、宿舍、报修、调宿、访客、卫生检查等日常管理': 'Manage students, dormitories, maintenance, transfers, visitors and inspections.',
  '查看个人宿舍信息，提交报修和调宿申请，维护个人资料': 'View room details, request maintenance and transfers, and update personal information.',
}

const messages = {
  成功: 'Success', 用户名或密码错误: 'Incorrect username or password.',
  用户未登录: 'Please sign in.', 用户会话过期: 'Your session has expired. Please sign in again.',
  重复操作: 'This operation has already been performed.',
  查询失败: 'Unable to load records.', 添加失败: 'Unable to add the record.',
  新增失败: 'Unable to add the record.', 更新失败: 'Unable to update the record.',
  修改失败: 'Unable to update the record.', 删除失败: 'Unable to delete the record.',
  禁止修改测试账号: 'This demo account cannot be edited.',
  禁止删除测试账号: 'This demo account cannot be deleted.',
  空宿舍查询失败: 'Unable to load rooms with vacancies.',
  该学生已有宿舍: 'This student already has a room.',
  查询首页住宿人数失败: 'Unable to load the resident count.',
  获取人数失败: 'Unable to load the occupancy count.',
  不存在该生: 'Student not found.', 不存在该学生: 'Student not found.',
  该房间人满了: 'This room is full.', 该床位已有人: 'This bed is already occupied.',
  不存在该房间: 'Room not found.', 金额不能为0: 'The amount cannot be zero.',
  宿舍号为必填项: 'A room number is required.',
  未查询到操作人宿舍: 'No room is assigned to this user.',
  rootFilePath为空: 'The avatar storage location is not configured.',
  设置头像失败: 'Unable to update the avatar.', 加载失败: 'Unable to load your session.',
  首页公告查询失败: 'Unable to load announcements.',
  报修统计查询失败: 'Unable to load maintenance statistics.',
}

export function displayText(value) {
  return Object.hasOwn(labels, value) ? labels[value] : value ?? ''
}

export function displayMessage(value) {
  if (value == null || value === '') return 'Unable to complete the operation. Please try again.'
  if (Object.hasOwn(messages, value)) return messages[value]
  if (/\p{Script=Han}/u.test(String(value))) {
    console.warn('Untranslated server message:', value)
    return 'Unable to complete the operation. Please try again.'
  }
  return String(value)
}

export const namePattern = /^(?=.{1,50}$)[A-Za-z\u4E00-\u9FFF]+(?:[ ·'’-][A-Za-z\u4E00-\u9FFF]+)*$/u

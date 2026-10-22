package com.example.springboot.controller;

import com.example.springboot.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/permission")
public class PermissionController {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/menus")
    public Result<?> menus(HttpSession session) {
        String roleCode = (String) session.getAttribute("Identity");
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return Result.error("-1", "用户未登录");
        }

        List<MenuNode> menus = loadMenus(roleCode);
        return Result.success(menus);
    }

    @GetMapping("/roles")
    public Result<?> roles() {
        String sql = "SELECT role_code roleCode, role_name roleName, description FROM sys_role ORDER BY id";
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    @GetMapping("/roleMenus")
    public Result<?> roleMenus() {
        String sql = "SELECT r.role_code roleCode, r.role_name roleName, m.menu_name menuName, " +
            "m.menu_path menuPath, m.permission_code permissionCode, m.sort_num sortNum " +
            "FROM sys_role r " +
            "JOIN sys_role_menu rm ON r.role_code = rm.role_code " +
            "JOIN sys_menu m ON rm.menu_id = m.id " +
            "ORDER BY r.id, m.sort_num";
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    private List<MenuNode> loadMenus(String roleCode) {
        String sql = "SELECT m.id, m.parent_id parentId, m.menu_name menuName, m.menu_path menuPath, " +
            "m.icon, m.sort_num sortNum, m.menu_type menuType, m.permission_code permissionCode, m.description " +
            "FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "WHERE rm.role_code = ? " +
            "ORDER BY m.sort_num";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, roleCode);
        Map<Integer, MenuNode> nodeMap = new LinkedHashMap<>();
        List<MenuNode> roots = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            MenuNode node = new MenuNode();
            node.setId(((Number) row.get("id")).intValue());
            node.setParentId(((Number) row.get("parentId")).intValue());
            node.setMenuName((String) row.get("menuName"));
            node.setMenuPath((String) row.get("menuPath"));
            node.setIcon((String) row.get("icon"));
            node.setMenuType((String) row.get("menuType"));
            node.setPermissionCode((String) row.get("permissionCode"));
            node.setDescription((String) row.get("description"));
            nodeMap.put(node.getId(), node);
        }

        for (MenuNode node : nodeMap.values()) {
            if (node.getParentId() == 0) {
                roots.add(node);
            } else {
                MenuNode parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        return roots;
    }

    public static class MenuNode {
        private Integer id;
        private Integer parentId;
        private String menuName;
        private String menuPath;
        private String icon;
        private String menuType;
        private String permissionCode;
        private String description;
        private List<MenuNode> children = new ArrayList<>();

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Integer getParentId() {
            return parentId;
        }

        public void setParentId(Integer parentId) {
            this.parentId = parentId;
        }

        public String getMenuName() {
            return menuName;
        }

        public void setMenuName(String menuName) {
            this.menuName = menuName;
        }

        public String getMenuPath() {
            return menuPath;
        }

        public void setMenuPath(String menuPath) {
            this.menuPath = menuPath;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getMenuType() {
            return menuType;
        }

        public void setMenuType(String menuType) {
            this.menuType = menuType;
        }

        public String getPermissionCode() {
            return permissionCode;
        }

        public void setPermissionCode(String permissionCode) {
            this.permissionCode = permissionCode;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<MenuNode> getChildren() {
            return children;
        }

        public void setChildren(List<MenuNode> children) {
            this.children = children;
        }
    }
}

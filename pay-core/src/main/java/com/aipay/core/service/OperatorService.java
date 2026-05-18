package com.aipay.core.service;

import com.aipay.core.domain.Operator;
import com.aipay.core.domain.OperatorPermission;
import com.aipay.core.mapper.OperatorMapper;
import com.aipay.core.mapper.OperatorPermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperatorService {

    private final OperatorMapper operatorMapper;
    private final OperatorPermissionMapper permissionMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public Operator createOperator(Long merchantId, String username,
                                    String rawPassword, String realName, boolean isAdmin) {
        Operator op = new Operator();
        op.setMerchantId(merchantId);
        op.setUsername(username);
        op.setPasswordHash(passwordEncoder.encode(rawPassword));
        op.setRealName(realName);
        op.setIsAdmin(isAdmin ? 1 : 0);
        op.setStatus(1);
        operatorMapper.insert(op);
        return op;
    }

    public Operator authenticate(String username, String rawPassword) {
        Operator op = operatorMapper.selectOne(
            new LambdaQueryWrapper<Operator>()
                .eq(Operator::getUsername, username)
                .eq(Operator::getStatus, 1));
        if (op == null || !passwordEncoder.matches(rawPassword, op.getPasswordHash())) {
            return null;
        }
        return op;
    }

    public List<OperatorPermission> getPermissions(Long operatorId) {
        return permissionMapper.selectList(
            new LambdaQueryWrapper<OperatorPermission>()
                .eq(OperatorPermission::getOperatorId, operatorId));
    }

    public void updatePermissions(Long operatorId, List<Map<String, Object>> permissions) {
        permissionMapper.delete(
            new LambdaQueryWrapper<OperatorPermission>()
                .eq(OperatorPermission::getOperatorId, operatorId));
        for (Map<String, Object> p : permissions) {
            OperatorPermission perm = new OperatorPermission();
            perm.setOperatorId(operatorId);
            perm.setModule((String) p.get("module"));
            perm.setCanView(((Number) p.getOrDefault("can_view", 0)).intValue());
            perm.setCanOperate(((Number) p.getOrDefault("can_operate", 0)).intValue());
            permissionMapper.insert(perm);
        }
    }
}

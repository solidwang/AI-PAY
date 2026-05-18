package com.aipay.core.service;

import com.aipay.common.exception.BizException;
import com.aipay.common.exception.ErrorCode;
import com.aipay.common.util.IdGenerator;
import com.aipay.core.domain.Merchant;
import com.aipay.core.mapper.MerchantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;

    public Merchant createMerchant(String name, String contactEmail, String contactPhone) {
        Merchant m = new Merchant();
        m.setMerchantNo(IdGenerator.merchantNo());
        m.setName(name);
        m.setContactEmail(contactEmail);
        m.setContactPhone(contactPhone);
        m.setStatus(1);
        merchantMapper.insert(m);
        return m;
    }

    public Merchant findById(Long id) {
        Merchant m = merchantMapper.selectById(id);
        if (m == null) throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        return m;
    }

    public Page<Merchant> listMerchants(int page, int size) {
        return merchantMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Merchant>().orderByDesc(Merchant::getCreatedAt));
    }

    public void updateMerchant(Long id, String name, String contactEmail,
                                String contactPhone, Integer status) {
        Merchant m = findById(id);
        if (name != null) m.setName(name);
        if (contactEmail != null) m.setContactEmail(contactEmail);
        if (contactPhone != null) m.setContactPhone(contactPhone);
        if (status != null) m.setStatus(status);
        merchantMapper.updateById(m);
    }
}

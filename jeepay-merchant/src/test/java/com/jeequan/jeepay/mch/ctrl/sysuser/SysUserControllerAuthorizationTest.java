package com.jeequan.jeepay.mch.ctrl.sysuser;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jeequan.jeepay.core.constants.ApiCodeEnum;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.SysUser;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.core.model.ApiRes;
import com.jeequan.jeepay.mch.ctrl.MerchantControllerTestSupport;
import com.jeequan.jeepay.mch.service.AuthService;
import com.jeequan.jeepay.service.impl.SysUserAuthService;
import com.jeequan.jeepay.service.impl.SysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SysUserControllerAuthorizationTest extends MerchantControllerTestSupport {

    private static final Long FOREIGN_USER_ID = 2001L;
    private static final Long OWN_USER_ID = 2002L;
    private static final Long OWN_ADMIN_USER_ID = 2003L;

    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysUserAuthService sysUserAuthService;
    @Mock
    private AuthService authService;
    @InjectMocks
    private SysUserController controller;

    @Test
    void rejectsDeletingOperatorOfAnotherMerchant() {
        // 越权目标：属于其他商户的操作员，recordId 由调用方任意指定。
        SysUser foreignOperator = user(FOREIGN_USER_ID, FOREIGN_MCH_NO);
        when(sysUserService.getById(FOREIGN_USER_ID)).thenReturn(foreignOperator);
        defaultAdminIs(OWN_ADMIN_USER_ID);

        BizException error = assertThrows(BizException.class, () -> controller.delete(FOREIGN_USER_ID));

        assertEquals(ApiCodeEnum.SYS_PERMISSION_ERROR.getCode(), error.getApiRes().getCode());
        verify(sysUserService, never()).removeUser(any(), anyString());
        verifyNoInteractions(authService);
    }

    @Test
    void deletesOperatorOfCurrentMerchant() {
        SysUser ownOperator = user(OWN_USER_ID, CURRENT_MCH_NO);
        when(sysUserService.getById(OWN_USER_ID)).thenReturn(ownOperator);
        defaultAdminIs(OWN_ADMIN_USER_ID);

        ApiRes<?> response = controller.delete(OWN_USER_ID);

        assertEquals(ApiCodeEnum.SUCCESS.getCode(), response.getCode());
        verify(sysUserService).removeUser(ownOperator, CS.SYS_TYPE.MCH);
        verify(authService).refAuthentication(List.of(OWN_USER_ID));
    }

    @Test
    void rejectsDeletingCurrentLoginUser() {
        SysUser currentUser = user(CURRENT_USER_ID, CURRENT_MCH_NO);
        when(sysUserService.getById(CURRENT_USER_ID)).thenReturn(currentUser);
        defaultAdminIs(OWN_ADMIN_USER_ID);

        BizException error = assertThrows(BizException.class, () -> controller.delete(CURRENT_USER_ID));

        assertEquals("系统不允许删除当前登陆用户！", error.getMessage());
        verify(sysUserService, never()).removeUser(any(), anyString());
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsDeletingCurrentMerchantDefaultAdmin() {
        SysUser defaultAdmin = user(OWN_ADMIN_USER_ID, CURRENT_MCH_NO);
        defaultAdmin.setIsAdmin(CS.YES);
        when(sysUserService.getById(OWN_ADMIN_USER_ID)).thenReturn(defaultAdmin);
        defaultAdminIs(OWN_ADMIN_USER_ID);

        BizException error = assertThrows(BizException.class, () -> controller.delete(OWN_ADMIN_USER_ID));

        assertEquals("系统不允许删除商户默认用户！", error.getMessage());
        verify(sysUserService, never()).removeUser(any(), anyString());
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsDeletingMissingOperator() {
        when(sysUserService.getById(FOREIGN_USER_ID)).thenReturn(null);
        defaultAdminIs(OWN_ADMIN_USER_ID);

        BizException error = assertThrows(BizException.class, () -> controller.delete(FOREIGN_USER_ID));

        assertEquals("该操作员不存在！", error.getMessage());
        verify(sysUserService, never()).removeUser(any(), anyString());
        verifyNoInteractions(authService);
    }

    /** 当前商户始终存在一个默认超管，避免既有超管保护逻辑掩盖缺失的归属校验。 */
    private void defaultAdminIs(Long adminUserId) {
        SysUser defaultAdmin = user(adminUserId, CURRENT_MCH_NO);
        defaultAdmin.setIsAdmin(CS.YES);
        lenient().when(sysUserService.getOne(ArgumentMatchers.<Wrapper<SysUser>>any()))
                .thenReturn(defaultAdmin);
    }
}

package org.dhorse.infrastructure.repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.dhorse.api.enums.LanguageTypeEnum;
import org.dhorse.api.enums.MessageCodeEnum;
import org.dhorse.api.enums.RoleTypeEnum;
import org.dhorse.api.enums.YesOrNoEnum;
import org.dhorse.api.result.PageData;
import org.dhorse.api.vo.App;
import org.dhorse.api.vo.AppExtendJava;
import org.dhorse.infrastructure.param.AppExtendJavaParam;
import org.dhorse.infrastructure.param.AppParam;
import org.dhorse.infrastructure.param.AppMemberParam;
import org.dhorse.infrastructure.repository.mapper.CustomizedBaseMapper;
import org.dhorse.infrastructure.repository.mapper.AppMapper;
import org.dhorse.infrastructure.repository.po.AppExtendJavaPO;
import org.dhorse.infrastructure.repository.po.AppPO;
import org.dhorse.infrastructure.repository.po.AppMemberPO;
import org.dhorse.infrastructure.strategy.login.dto.LoginUser;
import org.dhorse.infrastructure.utils.BeanUtils;
import org.dhorse.infrastructure.utils.Constants;
import org.dhorse.infrastructure.utils.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;

@Repository
public class AppRepository extends BaseRepository<AppParam, AppPO> {

	private static final Logger logger = LoggerFactory.getLogger(AppRepository.class);

	@Autowired
	private AppMapper appMapper;

	@Autowired
	private AppExtendJavaRepository appExtendJavaRepository;

	@Autowired
	private AppMemberRepository appMemberRepository;

	public PageData<App> page(LoginUser loginUser, AppParam bizParam) {
		// 如果admin角色的用户，直接查询应用表
		if (RoleTypeEnum.ADMIN.getCode().equals(loginUser.getRoleType())) {
			PageData<App> pageDto = pageData(super.page(bizParam));
			pageDto.getItems().forEach(e -> {
				e.setModifyRights(YesOrNoEnum.YES.getCode());
				e.setDeleteRights(YesOrNoEnum.YES.getCode());
			});
			return pageDto;
		}
		// 如果是普通用户，先查询应用成员表，再查询应用表
		AppMemberParam appMemberParam = new AppMemberParam();
		appMemberParam.setUserId(loginUser.getId());
		appMemberParam.setPageNum(bizParam.getPageNum());
		appMemberParam.setPageSize(bizParam.getPageSize());
		IPage<AppMemberPO> pageData = appMemberRepository.page(appMemberParam);
		if (CollectionUtils.isEmpty(pageData.getRecords())) {
			return pageData(bizParam);
		}
		Map<String, AppMemberPO> appMemberMap = pageData.getRecords().stream().collect(Collectors.toMap(AppMemberPO::getAppId, e -> e));
		bizParam.setIds(new ArrayList<>(appMemberMap.keySet()));
		PageData<App> pageDto = pageData(super.page(bizParam));
		//只有应用管理员才有修改（删除）权限
		for(App app : pageDto.getItems()) {
			AppMemberPO appUser = appMemberMap.get(app.getId());
			if(appUser.getLoginName().equals(loginUser.getLoginName())){
				String[] roleTypes = appUser.getRoleType().split(",");
				Set<Integer> roleSet = new HashSet<>();
				for (String role : roleTypes) {
					roleSet.add(Integer.valueOf(role));
				}
				List<Integer> adminRole = Constants.ROLE_OF_OPERATE_APP_USER.stream()
						.filter(item -> roleSet.contains(item))
						.collect(Collectors.toList());
				if(adminRole.size() > 0) {
					app.setModifyRights(YesOrNoEnum.YES.getCode());
					app.setDeleteRights(YesOrNoEnum.YES.getCode());
				}
			}

		}
		return pageDto;
	}

	public App query(LoginUser loginUser, AppParam bizParam) {
		if(bizParam.getId() == null) {
			return null;
		}
		if (RoleTypeEnum.ADMIN.getCode().equals(loginUser.getRoleType())) {
			return po2Dto(super.query(bizParam));
		}
		AppMemberPO appMember = appMemberRepository
				.queryByLoginNameAndAppId(loginUser.getLoginName(), bizParam.getId());
		if (appMember == null) {
			return null;
		}
		return po2Dto(super.query(bizParam));
	}

	public App queryWithExtendById(String id) {
		App app = po2Dto(super.queryById(id));
		queryAppExtend(app);
		return app;
	}

	public App queryWithExtendById(LoginUser loginUser, String id) {
		AppParam appParam = new AppParam();
		appParam.setId(id);
		App app = query(loginUser, appParam);
		queryAppExtend(app);
		return app;
	}

	public AppPO queryByAppName(String appName) {
		AppParam appInfoParam = new AppParam();
		appInfoParam.setAppName(appName);
		return super.query(appInfoParam);
	}

	private void queryAppExtend(App app) {
		if(app == null) {
			return;
		}
		if (LanguageTypeEnum.JAVA.getCode().equals(app.getLanguageType())) {
			AppExtendJavaParam appJavaInfoParam = new AppExtendJavaParam();
			appJavaInfoParam.setAppId(app.getId());
			AppExtendJavaPO appExtendJavaPO = appExtendJavaRepository.query(appJavaInfoParam);
			AppExtendJava appExtendJava = new AppExtendJava();
			BeanUtils.copyProperties(appExtendJavaPO, appExtendJava);
			app.setAppExtend(appExtendJava);
		}
	}

	public boolean update(LoginUser loginUser, AppParam bizParam) {
		if (!hasOperatingRights(loginUser, bizParam)) {
			LogUtils.throwException(logger, MessageCodeEnum.NO_ACCESS_RIGHT);
		}
		return super.updateById(bizParam);
	}

	public boolean delete(LoginUser loginUser, AppParam bizParam) {
		if (!hasOperatingRights(loginUser, bizParam)) {
			LogUtils.throwException(logger, MessageCodeEnum.NO_ACCESS_RIGHT);
		}
		return super.delete(bizParam.getId());
	}

	protected boolean hasOperatingRights(LoginUser loginUser, AppParam bizParam) {
		validateApp(bizParam.getId());
		AppPO e = queryById(bizParam.getId());
		if (e == null || !e.getId().equals(bizParam.getId())) {
			LogUtils.throwException(logger, MessageCodeEnum.RECORD_IS_INEXISTENCE);
		}

		if (RoleTypeEnum.ADMIN.getCode().equals(loginUser.getRoleType())) {
			return true;
		}
		AppMemberPO appUser = appMemberRepository
				.queryByLoginNameAndAppId(loginUser.getLoginName(), bizParam.getId());
		if (appUser == null || Objects.isNull(appUser.getRoleType())) {
			return false;
		}
		if (!appUser.getLoginName().equals(loginUser.getLoginName())) {
			return false;
		}
		String[] roleTypes = appUser.getRoleType().split(",");
		Set<Integer> roleSet = new HashSet<>();
		for (String role : roleTypes) {
			roleSet.add(Integer.valueOf(role));
		}
		List<Integer> adminRole = Constants.ROLE_OF_OPERATE_APP_USER.stream().filter(item -> roleSet.contains(item))
				.collect(Collectors.toList());
		return adminRole.size() > 0;
	}

	protected PageData<App> pageData(IPage<AppPO> pageEntity) {
		PageData<App> pageData = new PageData<>();
		pageData.setPageNum((int) pageEntity.getCurrent());
		pageData.setPageCount((int) pageEntity.getPages());
		pageData.setPageSize((int) pageEntity.getSize());
		pageData.setItemCount((int) pageEntity.getTotal());
		pageData.setItems(pos2Dtos(pageEntity.getRecords()));
		return pageData;
	}

	protected List<App> pos2Dtos(List<AppPO> pos) {
		return pos.stream().map(e -> po2Dto(e)).collect(Collectors.toList());
	}

	protected App po2Dto(AppPO e) {
		if (e == null) {
			return null;
		}
		App dto = new App();
		BeanUtils.copyProperties(e, dto);
		return dto;
	}

	protected PageData<App> pageData(AppParam bizParam) {
		PageData<App> pageData = new PageData<>();
		pageData.setPageNum(1);
		pageData.setPageCount(0);
		pageData.setPageSize(bizParam.getPageSize());
		pageData.setItemCount(0);
		pageData.setItems(null);
		return pageData;
	}

	@Override
	protected CustomizedBaseMapper<AppPO> getMapper() {
		return appMapper;
	}

	@Override
	protected AppPO updateCondition(AppParam bizParam) {
		AppPO po = new AppPO();
		po.setId(bizParam.getId());
		return po;
	}

}
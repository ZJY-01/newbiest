package com.newbiest.gc.rest.record.express;

import com.newbiest.mms.model.MaterialLot;
import com.newbiest.msg.RequestBody;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;


@Data
@ApiModel("具体请求操作信息")
public class RecordExpressRequestBody extends RequestBody {
	
	private static final long serialVersionUID = 1L;

	/**
	 * 自动下单，则需要关联跨域速递进行下单
	 */
	public static final String ACTION_TYPE_AUTO_ORDER = "AutoOrder";

	/**
	 * 手动下单，则只需要更新物料批次和单据的信息即可
	 */
	public static final String ACTION_TYPE_MANUAL_ORDER = "ManualOrder";

	public static final String ACTION_TYPE_CANCEL_ORDER = "CancelOrder";

	private String actionType;

	private Integer serviceMode;

	private Integer payMode;

	public String expressNumber;

	private List<MaterialLot> materialLots;

}

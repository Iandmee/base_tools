
package com.android.sdklib.repository.generated.repository.v2;

import com.android.sdklib.repository.generated.common.v2.ApiDetailsType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

/**
 * DO NOT EDIT This file was generated by xjc from sdk-repository-02.xsd. Any changes will be lost
 * upon recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>trivial type-details subclass for source components.
 *
 * <p>Java class for sourceDetailsType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="sourceDetailsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://schemas.android.com/sdk/android/repo/common/02}apiDetailsType"&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "sourceDetailsType")
@SuppressWarnings({"override", "unchecked"})
public class SourceDetailsType extends ApiDetailsType
        implements com.android.sdklib.repository.meta.DetailsTypes.SourceDetailsType {

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }

}

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.11.11 at 06:15:14 PM PST 
//


package com.android.sdklib.repositoryv2.generated.repository.v1;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import com.android.repository.impl.generated.v1.TypeDetails;


/**
 * 
 *                 trivial type-details subclass for ndk components.
 *             
 * 
 * <p>Java class for ndkDetailsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ndkDetailsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://schemas.android.com/repository/android/common/01}typeDetails"&gt;
 *       &lt;all&gt;
 *       &lt;/all&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ndkDetailsType")
@SuppressWarnings({
    "override",
    "unchecked"
})
public class NdkDetailsType
    extends TypeDetails
    implements com.android.sdklib.repositoryv2.meta.DetailsTypes.NdkDetailsType
{


    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }

}

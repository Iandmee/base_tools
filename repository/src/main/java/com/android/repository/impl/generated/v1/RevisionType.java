//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.11.04 at 02:23:40 PM PST 
//


package com.android.repository.impl.generated.v1;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * 
 *                 A full revision, with a major.minor.micro and an
 *                 optional preview number. The major number is mandatory.
 *             
 * 
 * <p>Java class for revisionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="revisionType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;all&gt;
 *         &lt;element name="major" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="minor" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="micro" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="preview" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/all&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "revisionType", propOrder = {

})
@SuppressWarnings({
    "override",
    "unchecked"
})
public class RevisionType
    extends com.android.repository.impl.meta.RevisionType
{

    protected int major;
    protected Integer minor;
    protected Integer micro;
    protected Integer preview;

    /**
     * Gets the value of the major property.
     * 
     */
    public int getMajor() {
        return major;
    }

    /**
     * Sets the value of the major property.
     * 
     */
    public void setMajor(int value) {
        this.major = value;
    }

    /**
     * Gets the value of the minor property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMinor() {
        return minor;
    }

    /**
     * Sets the value of the minor property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMinor(Integer value) {
        this.minor = value;
    }

    /**
     * Gets the value of the micro property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getMicro() {
        return micro;
    }

    /**
     * Sets the value of the micro property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setMicro(Integer value) {
        this.micro = value;
    }

    /**
     * Gets the value of the preview property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPreview() {
        return preview;
    }

    /**
     * Sets the value of the preview property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPreview(Integer value) {
        this.preview = value;
    }

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }

}

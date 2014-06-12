

/* First created by JCasGen Thu Jun 05 14:36:14 CEST 2014 */
package de.tudarmstadt.lt.wiki.uima.type;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;
import org.apache.uima.jcas.tcas.Annotation;


/** 
 * Updated by JCasGen Thu Jun 05 14:36:14 CEST 2014
 * XML source: /Users/jsimon/Projects/Workspace/wikiprocessor/src/main/java/uima/type/WikiLink.xml
 * @generated */
public class WikiLink extends Annotation {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(WikiLink.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated
   * @return index of the type  
   */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected WikiLink() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated
   * @param addr low level Feature Structure reference
   * @param type the type of this Feature Structure 
   */
  public WikiLink(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated
   * @param jcas JCas to which this Feature Structure belongs 
   */
  public WikiLink(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated
   * @param jcas JCas to which this Feature Structure belongs
   * @param begin offset to the begin spot in the SofA
   * @param end offset to the end spot in the SofA 
  */  
  public WikiLink(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** 
   * <!-- begin-user-doc -->
   * Write your own initialization here
   * <!-- end-user-doc -->
   *
   * @generated modifiable 
   */
  private void readObject() {/*default - does nothing empty block */}
     
 
    
  //*--------------*
  //* Feature: resource

  /** getter for resource - gets 
   * @generated
   * @return value of the feature 
   */
  public String getResource() {
    if (WikiLink_Type.featOkTst && ((WikiLink_Type)jcasType).casFeat_resource == null)
      jcasType.jcas.throwFeatMissing("resource", "uima.type.WikiLink");
    return jcasType.ll_cas.ll_getStringValue(addr, ((WikiLink_Type)jcasType).casFeatCode_resource);}
    
  /** setter for resource - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setResource(String v) {
    if (WikiLink_Type.featOkTst && ((WikiLink_Type)jcasType).casFeat_resource == null)
      jcasType.jcas.throwFeatMissing("resource", "uima.type.WikiLink");
    jcasType.ll_cas.ll_setStringValue(addr, ((WikiLink_Type)jcasType).casFeatCode_resource, v);}    
  }

    
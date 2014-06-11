
/* First created by JCasGen Thu Jun 05 14:36:14 CEST 2014 */
package uima.type;

import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.impl.FeatureImpl;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.tcas.Annotation_Type;

/** 
 * Updated by JCasGen Thu Jun 05 14:36:14 CEST 2014
 * @generated */
public class WikiLink_Type extends Annotation_Type {
  /** @generated 
   * @return the generator for this type
   */
  @Override
  protected FSGenerator getFSGenerator() {return fsGenerator;}
  /** @generated */
  private final FSGenerator fsGenerator = 
    new FSGenerator() {
      public FeatureStructure createFS(int addr, CASImpl cas) {
  			 if (WikiLink_Type.this.useExistingInstance) {
  			   // Return eq fs instance if already created
  		     FeatureStructure fs = WikiLink_Type.this.jcas.getJfsFromCaddr(addr);
  		     if (null == fs) {
  		       fs = new WikiLink(addr, WikiLink_Type.this);
  			   WikiLink_Type.this.jcas.putJfsFromCaddr(addr, fs);
  			   return fs;
  		     }
  		     return fs;
        } else return new WikiLink(addr, WikiLink_Type.this);
  	  }
    };
  /** @generated */
  public final static int typeIndexID = WikiLink.typeIndexID;
  /** @generated 
     @modifiable */
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("uima.type.WikiLink");
 
  /** @generated */
  final Feature casFeat_resource;
  /** @generated */
  final int     casFeatCode_resource;
  /** @generated
   * @param addr low level Feature Structure reference
   * @return the feature value 
   */ 
  public String getResource(int addr) {
        if (featOkTst && casFeat_resource == null)
      jcas.throwFeatMissing("resource", "uima.type.WikiLink");
    return ll_cas.ll_getStringValue(addr, casFeatCode_resource);
  }
  /** @generated
   * @param addr low level Feature Structure reference
   * @param v value to set 
   */    
  public void setResource(int addr, String v) {
        if (featOkTst && casFeat_resource == null)
      jcas.throwFeatMissing("resource", "uima.type.WikiLink");
    ll_cas.ll_setStringValue(addr, casFeatCode_resource, v);}
    
  



  /** initialize variables to correspond with Cas Type and Features
	 * @generated
	 * @param jcas JCas
	 * @param casType Type 
	 */
  public WikiLink_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

 
    casFeat_resource = jcas.getRequiredFeatureDE(casType, "resource", "uima.cas.String", featOkTst);
    casFeatCode_resource  = (null == casFeat_resource) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_resource).getCode();

  }
}



    
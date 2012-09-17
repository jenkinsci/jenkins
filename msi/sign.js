// digitally sign an MSI file by the specified PKCS12 key

var sc= new ActiveXObject('CAPICOM.SignedCode');
var signer = new ActiveXObject('CAPICOM.Signer');

var args = WScript.Arguments;

signer.Load(args(1));
sc.FileName = args(0);
sc.Description = args(2);
sc.DescriptionURL = "http://jenkins-ci.org/";
sc.Sign(signer);

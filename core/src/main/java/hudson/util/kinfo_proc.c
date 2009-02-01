/*
	Shows the offsets of various fields in kinfo_proc
*/
#include <stdio.h>
#include <sys/sysctl.h>

#define FIELD(x) \
	printf(#x "=%d\n", ((char*)(&kp.x))-((char*)&kp));

void main() {
 	struct kinfo_proc kp;
	printf("kinfo_proc=%d\n", sizeof(kp));
	
	FIELD(kp_proc.p_pid);
	FIELD(kp_proc.p_comm);
	FIELD(kp_eproc.e_ppid);
	FIELD(kp_eproc.e_pcred.p_ruid);
	FIELD(kp_eproc.e_pcred.p_rgid);
	FIELD(kp_eproc.e_ucred.cr_uid);

	printf("MAXCOMLEN=%d\n",MAXCOMLEN);
}

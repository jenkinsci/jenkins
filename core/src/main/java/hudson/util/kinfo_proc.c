/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/*
	Shows the offsets of various fields in kinfo_proc

Execution results from my laptop
--------------------------------
% ./a.out 
kinfo_proc=492
kp_proc.p_pid=24
kp_proc.p_comm=163
kp_eproc.e_ppid=416
kp_eproc.e_pcred.p_ruid=280
kp_eproc.e_pcred.p_rgid=288
kp_eproc.e_ucred.cr_uid=304
MAXCOMLEN=16
[~/ws/hudson/main/core/src/main/java/hudson/util@longhorn]
% uname -a
Darwin longhorn.local 8.11.1 Darwin Kernel Version 8.11.1: Wed Oct 10 18:23:28 PDT 2007; root:xnu-792.25.20~1/RELEASE_I386 i386 i386
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

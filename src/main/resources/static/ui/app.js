/* LiveKlass 로컬 콘솔 — 과제 API 확인용 SPA (docs/ui-plan.md 화면 구성도 기준). 라이브러리 없음. */
'use strict';

// ───────────────────────── 상태 · 공통 유틸 ─────────────────────────

const S = { auth: sessionStorage.getItem('auth'), me: null, unreadTimer: null };

const $ = (sel) => document.querySelector(sel);

function esc(v) {
	return String(v ?? '').replace(/[&<>"']/g, (c) =>
		({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const won = (n) => Number(n).toLocaleString('ko-KR') + '원';
const dt = (s) => s ? s.replace('T', ' ').slice(0, 16) : '-';

function toast(msg, isError) {
	const el = $('#toast');
	el.textContent = msg;
	el.className = isError ? 'error-toast' : 'ok-toast';
	clearTimeout(el._t);
	el._t = setTimeout(() => el.className = 'hidden', 3500);
}

async function api(method, path, body) {
	const headers = { Authorization: 'Basic ' + S.auth };
	if (body !== undefined) headers['Content-Type'] = 'application/json';
	const res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
	if (res.status === 401 && S.me) { logout(); throw new Error('인증이 만료되어 로그아웃했습니다.'); }
	const data = res.status === 204 ? null : await res.json().catch(() => null);
	if (!res.ok) throw new Error(data?.message || `요청 실패 (${res.status})`);
	return data;
}

const badge = (v) => `<span class="badge b-${esc(v)}">${esc(v)}</span>`;

function pager(p, go) {
	if (p.totalPages <= 1) return '';
	return `<div class="pager">
		<button class="btn small" ${p.page === 0 ? 'disabled' : ''} onclick="${go}(${p.page - 1})">이전</button>
		<span>${p.page + 1} / ${p.totalPages} (총 ${p.totalElements}건)</span>
		<button class="btn small" ${p.page >= p.totalPages - 1 ? 'disabled' : ''} onclick="${go}(${p.page + 1})">다음</button>
	</div>`;
}

// ───────────────────────── 인증 ─────────────────────────

const SEED_ACCOUNTS = ['admin-1', 'creator-1', 'creator-2', 'creator-3', 'student-1', 'student-2', 'student-3'];

async function login(email, password) {
	const auth = btoa(email + ':' + password);
	const res = await fetch('/api/users/me', { headers: { Authorization: 'Basic ' + auth } });
	if (!res.ok) throw new Error(res.status === 401 ? '이메일 또는 비밀번호가 올바르지 않습니다.' : `로그인 실패 (${res.status})`);
	S.auth = auth;
	S.me = await res.json();
	sessionStorage.setItem('auth', auth);
	enterApp();
}

function logout() {
	sessionStorage.removeItem('auth');
	clearInterval(S.unreadTimer);
	S.auth = null; S.me = null;
	location.hash = '';
	$('#app-view').classList.add('hidden');
	$('#login-view').classList.remove('hidden');
}

function enterApp() {
	$('#login-view').classList.add('hidden');
	$('#app-view').classList.remove('hidden');
	$('#me-info').innerHTML = `${esc(S.me.name)} ${badge(S.me.role)}`;
	buildMenu();
	if (!location.hash || location.hash === '#') location.hash = '#/courses';
	route();
	refreshUnread();
	clearInterval(S.unreadTimer);
	S.unreadTimer = setInterval(refreshUnread, 30000);
}

async function refreshUnread() {
	try {
		const p = await api('GET', '/api/notifications/me?isRead=false&size=1');
		const el = $('#menu-noti-badge');
		if (el) el.textContent = p.totalElements > 0 ? p.totalElements : '';
	} catch (e) { /* 뱃지 폴링 실패는 무시 */ }
}

// ───────────────────────── 메뉴 (화면 구성도 IA) · 라우터 ─────────────────────────

const ALL = ['ADMIN', 'CREATOR', 'STUDENT'];

const MENU_GROUPS = [
	{ title: '강의', items: [
		{ hash: '#/courses', label: '강의 목록', roles: ALL },
		{ hash: '#/courses/new', label: '강의 등록', roles: ['CREATOR'] },
	] },
	{ title: '내 수강신청', items: [
		{ hash: '#/enrollments', label: '신청 목록', roles: ['STUDENT'] },
	] },
	{ title: '판매 관리', items: [
		{ hash: '#/admin/sales', label: '판매 목록', roles: ['ADMIN'] },
	] },
	{ title: '판매/정산', items: [
		{ hash: '#/sales', label: '판매 목록', roles: ['CREATOR'] },
		{ hash: '#/settlements', label: '정산 목록', roles: ['CREATOR'] },
	] },
	{ title: '정산 관리', items: [
		{ hash: '#/admin/settlements', label: '정산 목록', roles: ['ADMIN'] },
	] },
	{ title: '정책', items: [
		{ hash: '#/admin/rates', label: '수수료 정책', roles: ['ADMIN'] },
	] },
	{ title: '알림', items: [
		{ hash: '#/admin/notifications', label: '알림센터', roles: ['ADMIN'] },
		{ hash: '#/notifications', label: '내 알림 <b id="menu-noti-badge" class="noti-badge"></b>', roles: ALL },
	] },
	{ title: '계정', items: [
		{ hash: '#/me', label: '내 정보', roles: ALL },
	] },
];

function buildMenu() {
	$('#side-menu').innerHTML = MENU_GROUPS
		.map((g) => ({ ...g, items: g.items.filter((m) => m.roles.includes(S.me.role)) }))
		.filter((g) => g.items.length)
		.map((g) => `<div class="nav-group">
			<div class="nav-group-title">${g.title}</div>
			${g.items.map((m) => `<a href="${m.hash}" data-hash="${m.hash}">${m.label}</a>`).join('')}
		</div>`).join('');
}

// 해시 → 뷰. 상세 라우트는 정규식으로 매칭
const ROUTES = [
	[/^#\/courses\/new$/, () => viewCourseCreate()],
	[/^#\/courses\/(.+)$/, (m) => viewCourseDetail(decodeURIComponent(m[1]))],
	[/^#\/courses$/, () => viewCourses()],
	[/^#\/enrollments\/(.+)$/, (m) => viewEnrollmentDetail(decodeURIComponent(m[1]))],
	[/^#\/enrollments$/, () => viewMyEnrollments()],
	[/^#\/sales$/, () => viewSales()],
	[/^#\/settlements$/, () => viewSettlements()],
	[/^#\/notifications$/, () => viewNotifications()],
	[/^#\/me$/, () => viewMe()],
	[/^#\/admin\/sales\/(.+)$/, (m) => viewAdminSaleDetail(decodeURIComponent(m[1]))],
	[/^#\/admin\/sales$/, () => viewAdminSaleList()],
	[/^#\/admin\/settlements\/(.+)$/, (m) => viewAdminSettlementDetail(decodeURIComponent(m[1]))],
	[/^#\/admin\/settlements$/, () => viewAdminSettlementList()],
	[/^#\/admin\/rates$/, () => viewAdminRates()],
	[/^#\/admin\/notifications$/, () => viewAdminNotifications()],
];

function route() {
	if (!S.me) return;
	const hash = location.hash || '#/courses';
	// 가장 긴 prefix가 일치하는 메뉴만 활성화
	let best = '';
	document.querySelectorAll('#side-menu a').forEach((a) => {
		if (hash.startsWith(a.dataset.hash) && a.dataset.hash.length > best.length) best = a.dataset.hash;
	});
	document.querySelectorAll('#side-menu a').forEach((a) =>
		a.classList.toggle('active', a.dataset.hash === best));
	for (const [re, fn] of ROUTES) {
		const m = hash.match(re);
		if (m) { Promise.resolve(fn(m)).catch((e) => toast(e.message, true)); return; }
	}
	viewCourses().catch((e) => toast(e.message, true));
}

// ───────────────────────── 강의 ─────────────────────────

async function viewCourses(page = 0) {
	const status = $('#course-status-filter')?.value ?? '';
	const p = await api('GET', `/api/courses?page=${page}&size=10${status ? '&status=' + status : ''}`);
	$('#content').innerHTML = `
		<h2>강의 목록</h2>
		<div class="toolbar">
			<select id="course-status-filter" onchange="viewCourses()">
				<option value="">전체 상태</option>
				${['DRAFT', 'OPEN', 'CLOSED'].map((s) => `<option ${s === status ? 'selected' : ''}>${s}</option>`).join('')}
			</select>
			${S.me.role === 'CREATOR' ? '<a class="btn primary" href="#/courses/new">강의 등록</a>' : ''}
		</div>
		<table>
			<tr><th>제목</th><th>크리에이터</th><th>가격</th><th>확정/정원</th><th>상태</th><th>기간</th></tr>
			${p.content.map((c) => `<tr class="click" onclick="location.hash='#/courses/${esc(c.id)}'">
				<td>${esc(c.title)}</td><td>${esc(c.creatorId)}</td><td>${won(c.price)}</td>
				<td>${c.confirmedCount} / ${c.capacity}</td><td>${badge(c.status)}</td>
				<td class="muted">${dt(c.startDate)} ~ ${dt(c.endDate)}</td></tr>`).join('')}
		</table>
		${pager(p, 'viewCourses')}`;
}

async function viewCourseCreate() {
	$('#content').innerHTML = `
		<p><a href="#/courses">← 강의 목록</a></p>
		<h2>강의 등록</h2>
		<div id="course-form-slot"></div>`;
	showCourseForm();
}

function showCourseForm(c) {
	$('#course-form-slot').innerHTML = `
		<form class="card form-grid" onsubmit="submitCourse(event, ${c ? `'${esc(c.id)}'` : 'null'})">
			<label>제목 <input name="title" required value="${esc(c?.title ?? '')}"></label>
			<label>설명 <input name="description" value="${esc(c?.description ?? '')}"></label>
			<label>가격 <input name="price" type="number" min="0" required value="${c?.price ?? 50000}"></label>
			<label>정원 <input name="capacity" type="number" min="1" required value="${c?.capacity ?? 10}"></label>
			<label>시작 <input name="startDate" type="datetime-local" value="${c?.startDate?.slice(0, 16) ?? ''}"></label>
			<label>종료 <input name="endDate" type="datetime-local" value="${c?.endDate?.slice(0, 16) ?? ''}"></label>
			<div><button class="btn primary">${c ? '수정' : '등록'}</button>
				${c ? '<button type="button" class="btn" onclick="this.closest(\'form\').remove()">닫기</button>' : ''}</div>
		</form>`;
}

async function submitCourse(ev, id) {
	ev.preventDefault();
	const f = ev.target;
	const body = {
		title: f.title.value, description: f.description.value,
		price: Number(f.price.value), capacity: Number(f.capacity.value),
		startDate: f.startDate.value || null, endDate: f.endDate.value || null,
	};
	const saved = id ? await api('PUT', `/api/courses/${id}`, body) : await api('POST', '/api/courses', body);
	toast(`강의 ${id ? '수정' : '등록'} 완료: ${saved.title}`);
	location.hash = `#/courses/${saved.id}`;
	route();
}

async function viewCourseDetail(id) {
	const c = await api('GET', `/api/courses/${id}`);
	const isAdmin = S.me.role === 'ADMIN';
	// A-5b: 수정·수강생 목록은 본인 CREATOR 또는 ADMIN, 상태 변경은 ADMIN 전용
	const canManage = isAdmin || (S.me.role === 'CREATOR' && c.creatorId === S.me.id);
	const transitions = { DRAFT: ['OPEN'], OPEN: ['CLOSED'], CLOSED: ['OPEN'] }[c.status] || [];
	$('#content').innerHTML = `
		<p><a href="#/courses">← 강의 목록</a></p>
		<h2>${esc(c.title)} ${badge(c.status)}</h2>
		<div class="card stat-row">
			<div><span class="muted">가격</span><b>${won(c.price)}</b></div>
			<div><span class="muted">확정 인원</span><b>${c.confirmedCount} / ${c.capacity}</b></div>
			<div><span class="muted">잔여석</span><b>${c.remainingSeats}</b></div>
			<div><span class="muted">결제 대기</span><b>${c.pendingCount}</b></div>
			<div><span class="muted">대기열</span><b>${c.waitlistedCount}</b></div>
		</div>
		<p>${esc(c.description) || '<span class="muted">설명 없음</span>'}</p>
		<p class="muted">기간: ${dt(c.startDate)} ~ ${dt(c.endDate)} · 크리에이터: ${esc(c.creatorId)}</p>
		<div class="toolbar">
			${S.me.role === 'STUDENT' ? `<button class="btn primary" onclick="applyCourse('${esc(c.id)}')">수강 신청</button>` : ''}
			${isAdmin ? transitions.map((t) =>
				`<button class="btn" onclick="changeCourseStatus('${esc(c.id)}','${t}')">${t}로 전환</button>`).join('') : ''}
			${canManage ? `<button class="btn" onclick='showCourseForm(${JSON.stringify(c).replace(/'/g, '&#39;')})'>강의 수정</button>` : ''}
		</div>
		<div id="course-form-slot"></div>
		<div id="course-enrollments"></div>`;
	if (canManage) renderCourseEnrollments(c.id).catch((e) => toast(e.message, true));
}

async function applyCourse(courseId) {
	const e = await api('POST', `/api/courses/${courseId}/enrollments`);
	toast(e.status === 'WAITLISTED' ? '만석 — 대기열에 등록되었습니다.' : '신청 완료 (결제 대기).');
	location.hash = `#/enrollments/${e.id}`;
}

async function changeCourseStatus(courseId, status) {
	await api('POST', `/api/courses/${courseId}/status`, { status });
	toast(`상태를 ${status}로 변경했습니다.`);
	route();
}

async function renderCourseEnrollments(courseId, page = 0) {
	const status = $('#ce-status-filter')?.value ?? '';
	const p = await api('GET',
		`/api/courses/${courseId}/enrollments?page=${page}&size=10${status ? '&status=' + status : ''}`);
	window.renderCourseEnrollmentsPage = (pg) => renderCourseEnrollments(courseId, pg);
	$('#course-enrollments').innerHTML = `
		<h3>수강생 목록</h3>
		<div class="toolbar"><select id="ce-status-filter" onchange="renderCourseEnrollments('${esc(courseId)}')">
			<option value="">전체</option>
			${['PENDING', 'CONFIRMED', 'WAITLISTED', 'CANCELLED'].map((s) =>
				`<option ${s === status ? 'selected' : ''}>${s}</option>`).join('')}
		</select></div>
		<table>
			<tr><th>수강생</th><th>상태</th><th>신청</th><th>확정</th><th>취소</th><th>결제 기한</th></tr>
			${p.content.map((e) => `<tr><td>${esc(e.studentName ?? e.studentId)}</td><td>${badge(e.status)}</td>
				<td>${dt(e.appliedAt)}</td><td>${dt(e.confirmedAt)}</td><td>${dt(e.cancelledAt)}</td>
				<td>${dt(e.paymentDueAt)}</td></tr>`).join('')}
		</table>
		${pager(p, 'renderCourseEnrollmentsPage')}`;
}

// ───────────────────────── 내 수강신청 (STUDENT): 목록 → 상세 ─────────────────────────

async function viewMyEnrollments(page = 0) {
	const status = $('#enr-status-filter')?.value ?? '';
	const p = await api('GET', `/api/enrollments/me?page=${page}&size=10${status ? '&status=' + status : ''}`);
	$('#content').innerHTML = `
		<h2>신청 목록</h2>
		<div class="toolbar"><select id="enr-status-filter" onchange="viewMyEnrollments()">
			<option value="">전체 상태</option>
			${['PENDING', 'CONFIRMED', 'WAITLISTED', 'CANCELLED'].map((s) =>
				`<option ${s === status ? 'selected' : ''}>${s}</option>`).join('')}
		</select></div>
		<table>
			<tr><th>강의</th><th>상태</th><th>신청 일시</th><th>결제 기한</th><th></th></tr>
			${p.content.map((e) => `<tr class="click" onclick="location.hash='#/enrollments/${e.id}'">
				<td>${esc(e.courseTitle ?? e.courseId)}</td>
				<td>${badge(e.status)}</td><td>${dt(e.appliedAt)}</td>
				<td>${e.paymentDueAt ? `<b class="warn">${dt(e.paymentDueAt)}</b>` : '-'}</td>
				<td class="muted">상세 →</td></tr>`).join('')}
		</table>
		${pager(p, 'viewMyEnrollments')}`;
}

async function viewEnrollmentDetail(id) {
	const e = await api('GET', `/api/enrollments/${id}`);
	const active = ['PENDING', 'CONFIRMED', 'WAITLISTED'].includes(e.status);
	$('#content').innerHTML = `
		<p><a href="#/enrollments">← 신청 목록</a></p>
		<h2>신청 상세 ${badge(e.status)}</h2>
		<div class="card">
			<div class="kv"><span>강의</span><a href="#/courses/${esc(e.courseId)}">${esc(e.courseTitle ?? e.courseId)}</a></div>
			<div class="kv"><span>신청 ID</span><span class="muted">${esc(e.id)}</span></div>
			<div class="kv"><span>신청 일시</span>${dt(e.appliedAt)}</div>
			<div class="kv"><span>확정 일시</span>${dt(e.confirmedAt)}</div>
			<div class="kv"><span>취소 일시</span>${dt(e.cancelledAt)}</div>
			${e.paymentDueAt ? `<div class="kv"><span>결제 기한</span><b class="warn">${dt(e.paymentDueAt)}</b> (대기열 승격 건 — 기한 내 결제 확정 필요)</div>` : ''}
		</div>
		<div id="waitlist-slot"></div>
		<div class="toolbar">
			${e.status === 'PENDING' ? `<button class="btn primary" onclick="confirmEnrollment('${e.id}')">결제 확정</button>` : ''}
			${active ? `<button class="btn danger" onclick="cancelEnrollment('${e.id}')">신청 취소</button>` : ''}
		</div>`;
	if (e.status === 'WAITLISTED') {
		const w = await api('GET', `/api/enrollments/${id}/waitlist-position`);
		$('#waitlist-slot').innerHTML =
			`<div class="card">대기 순번 <b>${w.position}</b>번 / 전체 대기 ${w.waitingCount}명</div>`;
	}
}

async function confirmEnrollment(id) {
	await api('POST', `/api/enrollments/${id}/confirm`);
	toast('결제 확정 완료 — 수강이 확정되었습니다.');
	viewEnrollmentDetail(id);
}

async function cancelEnrollment(id) {
	await api('POST', `/api/enrollments/${id}/cancel`);
	toast('취소되었습니다.');
	viewEnrollmentDetail(id);
}

// ───────────────────────── 판매/정산 (CREATOR) ─────────────────────────

async function viewSales(page = 0, tab) {
	tab = tab ?? window._salesTab ?? 'sales';
	window._salesTab = tab;
	const from = $('#sales-from')?.value ?? '';
	const to = $('#sales-to')?.value ?? '';
	const q = `page=${page}&size=10` + (from ? `&from=${from}T00:00:00` : '') + (to ? `&to=${to}T23:59:59` : '');
	const p = await api('GET', tab === 'sales' ? `/api/sales/my?${q}` : `/api/sales/my/cancels?${q}`);
	window.viewSalesPage = (pg) => viewSales(pg, tab);
	$('#content').innerHTML = `
		<h2>판매 목록 <span class="muted">(내 강의)</span></h2>
		<div class="toolbar">
			<button class="btn ${tab === 'sales' ? 'primary' : ''}" onclick="viewSales(0,'sales')">판매</button>
			<button class="btn ${tab === 'cancels' ? 'primary' : ''}" onclick="viewSales(0,'cancels')">환불</button>
			<input type="date" id="sales-from" value="${from}"> ~ <input type="date" id="sales-to" value="${to}">
			<button class="btn" onclick="viewSales(0,'${tab}')">조회</button>
		</div>
		${tab === 'sales' ? `<table>
			<tr><th>ID</th><th>강의</th><th>수강생</th><th>금액</th><th>요율</th><th>결제 일시</th></tr>
			${p.content.map((s) => `<tr><td class="muted">${esc(s.id)}</td><td>${esc(s.courseTitle ?? s.courseId)}</td>
				<td>${esc(s.studentId)}</td><td>${won(s.amount)}</td><td>${s.commissionRate}%</td>
				<td>${dt(s.paidAt)}</td></tr>`).join('')}
		</table>` : `<table>
			<tr><th>ID</th><th>원 판매</th><th>환불 금액</th><th>취소 일시</th></tr>
			${p.content.map((c) => `<tr><td class="muted">${esc(c.id)}</td><td class="muted">${esc(c.saleRecordId)}</td>
				<td class="warn">−${won(c.refundAmount)}</td><td>${dt(c.cancelledAt)}</td></tr>`).join('')}
		</table>`}
		${pager(p, 'viewSalesPage')}`;
}

function monthlyCard(m) {
	const neg = m.payoutAmount < 0;
	return `<div class="card stat-row">
		<div><span class="muted">총 판매</span><b>${won(m.totalSalesAmount)}</b>${'salesCount' in m ? `<span class="muted">${m.salesCount}건</span>` : ''}</div>
		<div><span class="muted">환불</span><b class="warn">−${won(m.refundAmount)}</b>${'cancelCount' in m ? `<span class="muted">${m.cancelCount}건</span>` : ''}</div>
		<div><span class="muted">순 판매</span><b>${won(m.netSalesAmount)}</b></div>
		<div><span class="muted">수수료</span><b>${won(m.commissionAmount)}</b></div>
		<div><span class="muted">정산 예정</span><b class="${neg ? 'warn' : 'ok'}">${won(m.payoutAmount)}</b></div>
	</div>`;
}

async function viewSettlements() {
	const month = $('#stl-month')?.value || new Date().toISOString().slice(0, 7);
	const m = await api('GET', `/api/settlements/monthly?creatorId=${S.me.id}&month=${month}`);
	const hist = await api('GET', '/api/settlements/my?size=10');
	$('#content').innerHTML = `
		<h2>정산 목록 <span class="muted">(내 정산)</span></h2>
		<div class="toolbar">
			<input type="month" id="stl-month" value="${month}">
			<button class="btn primary" onclick="viewSettlements()">월별 정산 조회</button>
		</div>
		<h3>${month} 실시간 집계</h3>
		${monthlyCard(m)}
		<h3>확정 이력</h3>
		<table>
			<tr><th>기간</th><th>정산 예정</th><th>상태</th><th></th></tr>
			${hist.content.map((s) => `<tr><td>${s.periodStart} ~ ${s.periodEnd}</td>
				<td>${won(s.payoutAmount)}</td><td>${badge(s.status)}</td>
				<td><button class="btn small" onclick="showSettlementDetail('${s.id}','settlement-detail')">상세</button></td>
			</tr>`).join('') || '<tr><td colspan="4" class="muted">확정된 정산이 없습니다</td></tr>'}
		</table>
		<div id="settlement-detail"></div>`;
}

async function showSettlementDetail(id, slot) {
	const d = await api('GET', `/api/settlements/${id}`);
	$('#' + slot).innerHTML = `
		<h3>정산 상세 — ${d.settlement.periodStart} ~ ${d.settlement.periodEnd} ${badge(d.settlement.status)}</h3>
		${monthlyCard(d.settlement)}
		${detailLinesTable(d.details)}`;
}

function detailLinesTable(details) {
	return `<table>
		<tr><th>구분</th><th>참조</th><th>금액</th></tr>
		${details.map((l) => `<tr><td>${badge(l.recordType)}</td>
			<td class="muted">${esc(l.saleRecordId ?? l.cancelRecordId)}</td>
			<td class="${l.amount < 0 ? 'warn' : ''}">${won(l.amount)}</td></tr>`).join('')}
		<tr><th colspan="2">라인 합계 (= 순 판매)</th><th>${won(details.reduce((a, l) => a + l.amount, 0))}</th></tr>
	</table>`;
}

// ───────────────────────── 알림: 내 알림 ─────────────────────────

async function viewNotifications(page = 0) {
	const isRead = $('#noti-filter')?.value ?? '';
	const p = await api('GET', `/api/notifications/me?page=${page}&size=10${isRead !== '' ? '&isRead=' + isRead : ''}`);
	$('#content').innerHTML = `
		<h2>내 알림</h2>
		<div class="toolbar">
			<select id="noti-filter" onchange="viewNotifications()">
				<option value="">전체</option>
				<option value="false" ${isRead === 'false' ? 'selected' : ''}>안읽음</option>
				<option value="true" ${isRead === 'true' ? 'selected' : ''}>읽음</option>
			</select>
			<button class="btn" onclick="viewNotifications(${page})">새로고침</button>
			<button class="btn" onclick="toggleNotiForm()">알림 접수 테스트</button>
		</div>
		<div id="noti-form-slot"></div>
		<table>
			<tr><th>타입</th><th>채널</th><th>발송 상태</th><th>참조</th><th>생성</th><th></th></tr>
			${p.content.map((n) => `<tr class="${n.isRead ? 'read' : ''}">
				<td>${esc(n.type)}</td><td>${esc(n.channel)}</td>
				<td>${badge(n.status)}${n.retryCount ? ` <span class="muted">재시도 ${n.retryCount}</span>` : ''}</td>
				<td class="muted">${esc(n.referenceId ?? '-')}</td><td>${dt(n.createdAt)}</td>
				<td>${n.isRead ? '' : `<button class="btn small" onclick="readNotification('${n.id}',${page})">읽음</button>`}</td>
			</tr>`).join('')}
		</table>
		${pager(p, 'viewNotifications')}`;
}

async function readNotification(id, page) {
	await api('POST', `/api/notifications/${id}/read`);
	viewNotifications(page);
	refreshUnread();
}

function toggleNotiForm() {
	const slot = $('#noti-form-slot');
	if (slot.innerHTML) { slot.innerHTML = ''; return; }
	slot.innerHTML = `
		<form class="card form-grid" onsubmit="submitNotification(event)">
			<label>수신자 <input name="recipientId" required value="${esc(S.me.id)}"></label>
			<label>멱등키(eventId) <input name="eventId" required value="manual-${Date.now()}"></label>
			<label>채널 <select name="channel"><option>IN_APP</option><option>EMAIL</option></select></label>
			<label>참조 <input name="referenceId" placeholder="course-1 (선택)"></label>
			<div><button class="btn primary">접수 (202)</button>
				<span class="muted">같은 멱등키로 다시 누르면 200 + 동일 id (C-6)</span></div>
		</form>`;
}

async function submitNotification(ev) {
	ev.preventDefault();
	const f = ev.target;
	const res = await fetch('/api/notifications', {
		method: 'POST',
		headers: { Authorization: 'Basic ' + S.auth, 'Content-Type': 'application/json' },
		body: JSON.stringify({
			recipientId: f.recipientId.value, type: 'GENERAL', channel: f.channel.value,
			eventId: f.eventId.value, referenceId: f.referenceId.value || null,
		}),
	});
	const data = await res.json();
	if (!res.ok) { toast(data.message || '접수 실패', true); return; }
	toast(`${res.status === 202 ? '신규 접수(202)' : '기존 요청 반환(200, 멱등)'} — id: ${data.id}`);
	viewNotifications();
}

// ───────────────────────── 내 정보 ─────────────────────────

async function viewMe() {
	const me = await api('GET', '/api/users/me');
	$('#content').innerHTML = `
		<h2>내 정보</h2>
		<div class="card">
			<div class="kv"><span>이름</span><b>${esc(me.name)}</b></div>
			<div class="kv"><span>ID</span><span class="muted">${esc(me.id)}</span></div>
			<div class="kv"><span>이메일</span>${esc(me.email)}</div>
			<div class="kv"><span>역할</span>${badge(me.role)}</div>
		</div>
		<p class="muted">인증은 HTTP Basic — 자격증명은 이 탭(sessionStorage)에만 보관되며 로그아웃 시 삭제됩니다.</p>`;
}

// ───────────────────────── ADMIN: 판매 관리 (목록 → 상세) ─────────────────────────

async function viewAdminSaleList(page = 0) {
	const from = $('#asale-from')?.value ?? '';
	const to = $('#asale-to')?.value ?? '';
	const q = `page=${page}&size=10` + (from ? `&from=${from}T00:00:00` : '') + (to ? `&to=${to}T23:59:59` : '');
	const p = await api('GET', `/api/admin/sales?${q}`);
	$('#content').innerHTML = `
		<h2>판매 목록 <span class="muted">(전체)</span></h2>
		<div class="toolbar">
			<input type="date" id="asale-from" value="${from}"> ~ <input type="date" id="asale-to" value="${to}">
			<button class="btn" onclick="viewAdminSaleList()">조회</button>
			<button class="btn primary" onclick="toggleAdminSaleForm()">판매 등록</button>
		</div>
		<div id="asale-form-slot"></div>
		<table>
			<tr><th>ID</th><th>강의</th><th>수강생</th><th>금액</th><th>요율</th><th>결제 일시</th><th></th></tr>
			${p.content.map((s) => `<tr class="click" onclick="location.hash='#/admin/sales/${esc(s.id)}'">
				<td class="muted">${esc(s.id)}</td><td>${esc(s.courseTitle ?? s.courseId)}</td>
				<td>${esc(s.studentId)}</td><td>${won(s.amount)}</td><td>${s.commissionRate}%</td>
				<td>${dt(s.paidAt)}</td><td class="muted">상세 →</td></tr>`).join('')}
		</table>
		${pager(p, 'viewAdminSaleList')}`;
}

function toggleAdminSaleForm() {
	const slot = $('#asale-form-slot');
	if (slot.innerHTML) { slot.innerHTML = ''; return; }
	const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
	slot.innerHTML = `
		<form class="card form-grid" onsubmit="submitAdminSale(event)">
			<label>ID(선택) <input name="id" placeholder="sale-99 (비우면 UUID)"></label>
			<label>강의 <input name="courseId" required placeholder="course-1"></label>
			<label>수강생 <input name="studentId" required placeholder="student-1"></label>
			<label>금액 <input name="amount" type="number" min="1" required value="50000"></label>
			<label>결제 일시 <input name="paidAt" type="datetime-local" required value="${now}"></label>
			<div><button class="btn primary">등록</button></div>
		</form>`;
}

async function submitAdminSale(ev) {
	ev.preventDefault();
	const f = ev.target;
	const s = await api('POST', '/api/admin/sales', {
		id: f.id.value || null, courseId: f.courseId.value, studentId: f.studentId.value,
		amount: Number(f.amount.value), paidAt: f.paidAt.value + ':00',
	});
	toast(`판매 등록 완료: ${s.id}`);
	location.hash = `#/admin/sales/${s.id}`;
}

async function viewAdminSaleDetail(id) {
	const [s, cancels] = await Promise.all([
		api('GET', `/api/admin/sales/${id}`),
		api('GET', `/api/admin/sales/${id}/cancels`),
	]);
	const refunded = cancels.reduce((a, c) => a + c.refundAmount, 0);
	const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
	$('#content').innerHTML = `
		<p><a href="#/admin/sales">← 판매 목록</a></p>
		<h2>판매 상세 <span class="muted">${esc(s.id)}</span></h2>
		<div class="card">
			<div class="kv"><span>강의</span><a href="#/courses/${esc(s.courseId)}">${esc(s.courseTitle ?? s.courseId)}</a></div>
			<div class="kv"><span>수강생</span>${esc(s.studentId)}</div>
			<div class="kv"><span>결제 금액</span><b>${won(s.amount)}</b></div>
			<div class="kv"><span>요율 스냅샷</span>${s.commissionRate}%</div>
			<div class="kv"><span>결제 일시</span>${dt(s.paidAt)}</div>
			<div class="kv"><span>수강신청 연결</span>${s.enrollmentId
				? `<span class="muted">${esc(s.enrollmentId)}</span> (결제 확정 자동 생성)` : '<span class="muted">없음 (API 등록)</span>'}</div>
			<div class="kv"><span>누적 환불 / 잔여</span><b class="warn">−${won(refunded)}</b> / <b>${won(s.amount - refunded)}</b></div>
		</div>
		<h3>취소/환불 이력</h3>
		<table>
			<tr><th>ID</th><th>환불 금액</th><th>취소 일시</th></tr>
			${cancels.map((c) => `<tr><td class="muted">${esc(c.id)}</td>
				<td class="warn">−${won(c.refundAmount)}</td><td>${dt(c.cancelledAt)}</td></tr>`).join('')
				|| '<tr><td colspan="3" class="muted">취소/환불 없음</td></tr>'}
		</table>
		<h3>취소(환불) 등록</h3>
		<form class="card form-grid" onsubmit="submitAdminCancel(event, '${esc(s.id)}')">
			<label>취소 ID(선택) <input name="id" placeholder="cancel-99"></label>
			<label>환불 금액 <input name="refundAmount" type="number" min="1" required value="${s.amount - refunded || 1}"></label>
			<label>취소 일시 <input name="cancelledAt" type="datetime-local" required value="${now}"></label>
			<div><button class="btn primary" ${s.amount - refunded <= 0 ? 'disabled' : ''}>등록</button>
				<span class="muted">누적 환불이 원금을 넘으면 409 (B-2)</span></div>
		</form>`;
}

async function submitAdminCancel(ev, saleRecordId) {
	ev.preventDefault();
	const f = ev.target;
	const c = await api('POST', `/api/admin/sales/${saleRecordId}/cancels`, {
		id: f.id.value || null, refundAmount: Number(f.refundAmount.value), cancelledAt: f.cancelledAt.value + ':00',
	});
	toast(`취소 등록 완료: ${c.id}`);
	viewAdminSaleDetail(saleRecordId);
}

// ───────────────────────── ADMIN: 수수료 정책 ─────────────────────────

async function viewAdminRates() {
	const rates = await api('GET', '/api/admin/commission-rates');
	$('#content').innerHTML = `
		<h2>수수료 정책</h2>
		<form class="card form-grid" onsubmit="submitRate(event)">
			<label>크리에이터 <input name="creatorId" placeholder="비우면 전체 기본 요율"></label>
			<label>요율(%) <input name="rate" type="number" step="0.01" min="0.01" max="99.99" required value="15.00"></label>
			<label>시작일 <input name="startedAt" type="date" required></label>
			<label>마감일 <input name="endedAt" type="date"></label>
			<div><button class="btn primary">등록</button>
				<span class="muted">동일 대상 기간 겹침은 409</span></div>
		</form>
		<table>
			<tr><th>대상</th><th>요율</th><th>적용 기간</th><th>등록자</th></tr>
			${rates.map((r) => `<tr><td>${esc(r.creatorId ?? '전체 기본')}</td><td>${r.rate}%</td>
				<td>${r.startedAt} ~ ${r.endedAt ?? '계속'}</td><td class="muted">${esc(r.adminId)}</td></tr>`).join('')}
		</table>`;
	$('input[name=startedAt]').value = new Date().toISOString().slice(0, 10);
}

async function submitRate(ev) {
	ev.preventDefault();
	const f = ev.target;
	await api('POST', '/api/admin/commission-rates', {
		creatorId: f.creatorId.value || null, rate: f.rate.value,
		startedAt: f.startedAt.value, endedAt: f.endedAt.value || null,
	});
	toast('요율 등록 완료 — 이후 판매분부터 스냅샷 적용 (B-3)');
	viewAdminRates();
}

// ───────────────────────── ADMIN: 정산 관리 (목록 → 상세 → 생성/상태 변경) ─────────────────────────

async function viewAdminSettlementList(page = 0) {
	const p = await api('GET', `/api/admin/settlements?page=${page}&size=10`);
	$('#content').innerHTML = `
		<h2>정산 목록 <span class="muted">(전체)</span></h2>
		<table>
			<tr><th>크리에이터</th><th>기간</th><th>순 판매</th><th>정산 예정</th><th>상태</th><th></th></tr>
			${p.content.map((s) => `<tr class="click" onclick="location.hash='#/admin/settlements/${s.id}'">
				<td>${esc(s.creatorId)}</td><td>${s.periodStart} ~ ${s.periodEnd}</td>
				<td>${won(s.netSalesAmount)}</td><td class="${s.payoutAmount < 0 ? 'warn' : ''}">${won(s.payoutAmount)}</td>
				<td>${badge(s.status)}</td><td class="muted">상세 →</td></tr>`).join('')
				|| '<tr><td colspan="6" class="muted">확정된 정산이 없습니다 — 아래에서 생성</td></tr>'}
		</table>
		${pager(p, 'viewAdminSettlementList')}
		<h3>정산 생성 <span class="muted">(기간 집계 → 크리에이터별 확정)</span></h3>
		<div class="toolbar">
			<input type="date" id="agg-from" value="${$('#agg-from')?.value || ''}"> ~
			<input type="date" id="agg-to" value="${$('#agg-to')?.value || ''}">
			<button class="btn" onclick="renderAggregate()">기간 집계</button>
		</div>
		<div id="aggregate-slot"></div>`;
	if (!$('#agg-from').value) {
		$('#agg-from').value = new Date().toISOString().slice(0, 8) + '01';
		$('#agg-to').value = new Date().toISOString().slice(0, 10);
	}
}

async function renderAggregate() {
	const from = $('#agg-from').value, to = $('#agg-to').value;
	const agg = await api('GET', `/api/admin/settlements/aggregate?from=${from}&to=${to}`);
	$('#aggregate-slot').innerHTML = `
		<table>
			<tr><th>크리에이터</th><th>총 판매</th><th>환불</th><th>순 판매</th><th>수수료</th><th>정산 예정</th><th>건수</th><th></th></tr>
			${agg.creators.map((c) => `<tr>
				<td>${esc(c.creatorName ?? c.creatorId)}</td><td>${won(c.totalSalesAmount)}</td>
				<td class="warn">−${won(c.refundAmount)}</td><td>${won(c.netSalesAmount)}</td>
				<td>${won(c.commissionAmount)}</td><td class="${c.payoutAmount < 0 ? 'warn' : 'ok'}">${won(c.payoutAmount)}</td>
				<td class="muted">판매 ${c.salesCount} / 취소 ${c.cancelCount}</td>
				<td><button class="btn small primary" onclick="createSettlement('${esc(c.creatorId)}','${from}','${to}')">정산 확정</button></td>
			</tr>`).join('') || '<tr><td colspan="8" class="muted">기간 내 판매/취소 없음</td></tr>'}
			<tr><th colspan="5">전체 합계</th><th>${won(agg.totalPayoutAmount)}</th><th colspan="2"></th></tr>
		</table>`;
}

async function createSettlement(creatorId, periodStart, periodEnd) {
	const d = await api('POST', '/api/admin/settlements', { creatorId, periodStart, periodEnd });
	toast(`정산 확정 생성: ${d.settlement.id} (PENDING)`);
	location.hash = `#/admin/settlements/${d.settlement.id}`;
}

async function viewAdminSettlementDetail(id) {
	const d = await api('GET', `/api/settlements/${id}`);
	const s = d.settlement;
	const next = { PENDING: 'CONFIRMED', CONFIRMED: 'PAID' }[s.status];
	$('#content').innerHTML = `
		<p><a href="#/admin/settlements">← 정산 목록</a></p>
		<h2>정산 상세 ${badge(s.status)}</h2>
		<div class="card">
			<div class="kv"><span>정산 ID</span><span class="muted">${esc(s.id)}</span></div>
			<div class="kv"><span>크리에이터</span>${esc(s.creatorId)}</div>
			<div class="kv"><span>기간</span>${s.periodStart} ~ ${s.periodEnd}</div>
			<div class="kv"><span>처리자</span>${esc(s.adminId)}</div>
			${s.confirmedAt ? `<div class="kv"><span>확정 일시</span>${dt(s.confirmedAt)}</div>` : ''}
			${s.paidAt ? `<div class="kv"><span>지급 일시</span>${dt(s.paidAt)}</div>` : ''}
		</div>
		${monthlyCard(s)}
		<div class="toolbar">
			${next ? `<button class="btn primary" onclick="changeSettlementStatus('${s.id}','${next}')">${next}로 전이</button>`
				: '<span class="muted">최종 상태(PAID)</span>'}
		</div>
		${detailLinesTable(d.details)}`;
}

async function changeSettlementStatus(id, status) {
	await api('POST', `/api/admin/settlements/${id}/status`, { status });
	toast(`상태 전이 완료: ${status}`);
	viewAdminSettlementDetail(id);
}

// ───────────────────────── ADMIN: 알림센터 (목록 → 상세 → 재발송) ─────────────────────────

async function viewAdminNotifications(page = 0) {
	const status = $('#anoti-status')?.value ?? '';
	const p = await api('GET', `/api/admin/notifications?page=${page}&size=10${status ? '&status=' + status : ''}`);
	$('#content').innerHTML = `
		<h2>알림센터 <span class="muted">(전체 알림)</span></h2>
		<div class="toolbar">
			<select id="anoti-status" onchange="viewAdminNotifications()">
				<option value="">전체 상태</option>
				${['PENDING', 'PROCESSING', 'SENT', 'RETRY_WAIT', 'DEAD'].map((s) =>
					`<option ${s === status ? 'selected' : ''}>${s}</option>`).join('')}
			</select>
			<button class="btn" onclick="viewAdminNotifications(${page})">새로고침</button>
		</div>
		<table>
			<tr><th>수신자</th><th>타입</th><th>채널</th><th>상태</th><th>재시도</th><th>생성</th><th></th></tr>
			${p.content.map((n) => `<tr class="click" onclick="showAdminNotiDetail('${n.id}')">
				<td>${esc(n.recipientId)}</td><td>${esc(n.type)}</td><td>${esc(n.channel)}</td>
				<td>${badge(n.status)}</td><td>${n.retryCount || '-'}</td><td>${dt(n.createdAt)}</td>
				<td class="muted">상세 →</td></tr>`).join('')}
		</table>
		${pager(p, 'viewAdminNotifications')}
		<div id="admin-noti-detail"></div>`;
}

async function showAdminNotiDetail(id) {
	const n = await api('GET', `/api/notifications/${id}`);
	$('#admin-noti-detail').innerHTML = `
		<h3>알림 상세</h3>
		<div class="card">
			<div class="kv"><span>ID</span><span class="muted">${esc(n.id)}</span></div>
			<div class="kv"><span>수신자</span>${esc(n.recipientId)}</div>
			<div class="kv"><span>타입 / 채널</span>${esc(n.type)} / ${esc(n.channel)}</div>
			<div class="kv"><span>상태</span>${badge(n.status)} ${n.retryCount ? `(실패 ${n.retryCount}회)` : ''}</div>
			<div class="kv"><span>멱등키</span><span class="muted">${esc(n.eventId)}</span></div>
			<div class="kv"><span>참조</span><span class="muted">${esc(n.referenceId ?? '-')}</span></div>
			<div class="kv"><span>실패 사유</span>${n.failureReason ? `<b class="warn">${esc(n.failureReason)}</b>` : '<span class="muted">없음</span>'}</div>
			${n.nextRetryAt ? `<div class="kv"><span>다음 재시도</span>${dt(n.nextRetryAt)}</div>` : ''}
			<div class="toolbar">
				${n.status === 'DEAD'
					? `<button class="btn primary" onclick="retryNotification('${n.id}')">수동 재발송 (카운트 초기화, C-7)</button>`
					: '<span class="muted">수동 재발송은 DEAD 상태에서만 가능합니다.</span>'}
			</div>
		</div>`;
	$('#admin-noti-detail').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

async function retryNotification(id) {
	await api('POST', `/api/admin/notifications/${id}/retry`);
	toast('PENDING으로 복귀 — 다음 폴링(10초)에 재발송됩니다.');
	showAdminNotiDetail(id);
}

// ───────────────────────── 초기화 ─────────────────────────

$('#quick-accounts').innerHTML = SEED_ACCOUNTS.map((id) =>
	`<button type="button" class="btn small" onclick="fillLogin('${id}')">${id}</button>`).join('');

function fillLogin(id) {
	$('#login-email').value = `${id}@liveklass.local`;
	$('#login-password').value = `${id}!`;
}

$('#login-form').addEventListener('submit', async (ev) => {
	ev.preventDefault();
	const err = $('#login-error');
	err.classList.add('hidden');
	try {
		await login($('#login-email').value, $('#login-password').value);
	} catch (e) {
		err.textContent = e.message;
		err.classList.remove('hidden');
	}
});

$('#logout-btn').addEventListener('click', logout);
window.addEventListener('hashchange', route);

(async function init() {
	if (S.auth) {
		try {
			S.me = await api('GET', '/api/users/me');
			enterApp();
			return;
		} catch (e) { sessionStorage.removeItem('auth'); S.auth = null; }
	}
	$('#login-view').classList.remove('hidden');
})();

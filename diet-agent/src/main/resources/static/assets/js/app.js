(function () {
    "use strict";
    const app = document.getElementById("app");
    const toast = document.getElementById("toast");
    const authUserLabel = document.getElementById("authUserLabel");
    const logoutButton = document.getElementById("logoutButton");
    const authPanel = document.getElementById("authPanel");
    const authTitle = document.getElementById("authTitle");
    const loginForm = document.getElementById("loginForm");
    const registerForm = document.getElementById("registerForm");
    const SLOT_LABELS = {
        mealTime: "用餐时间",
        mood: "心情状态",
        scene: "用餐场景",
        healthGoal: "健康目标",
        cuisine: "菜系偏好",
        taste: "口味偏好",
        convenience: "便利程度"
    };
    const INTENTS = [
        "MEAL_RECOMMENDATION",
        "CLARIFY_NEEDED",
        "MEAL_ADJUST",
        "MEAL_PLAN",
        "HEALTH_RISK",
        "OTHER"
    ];
    const state = {
        home: { loaded: false, personalCount: 0, publicCount: 0 },
        profile: { loaded: false, loading: false, data: null },
        checkins: defaultCheckinState(),
        slotOptions: null,
        personalMeals: [],
        publicMeals: [],
        editingMeal: null,
        chat: {
            sourceMode: "PERSONAL",
            sessionId: null,
            sending: false,
            messages: [
                {
                    role: "assistant",
                    text: "你好，我可以根据你的个人餐食库或公共餐食库推荐今天吃什么。可以试试问我：今晚想吃清淡一点，有什么推荐？"
                }
            ]
        },
        traces: {
            rows: [],
            selected: null,
            loading: false,
            filters: defaultTraceFilters()
        },
        evaluation: {
            report: null,
            loading: false,
            form: defaultRangeForm()
        }
    };
    function defaultRangeForm() {
        const end = new Date();
        const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
        return {
            startAt: toLocalInputValue(start),
            endAt: toLocalInputValue(end),
            limit: 50,
            includeLlmJudge: false
        };
    }
    function defaultTraceFilters() {
        const range = defaultRangeForm();
        return {
            startAt: range.startAt,
            endAt: range.endAt,
            onlyUnlabeled: false,
            limit: 50,
            sessionId: ""
        };
    }
    function toLocalInputValue(date) {
        const pad = (value) => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }
    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
    function safeJson(value) {
        if (value === null || value === undefined || value === "") {
            return "";
        }
        try {
            const parsed = typeof value === "string" ? JSON.parse(value) : value;
            return JSON.stringify(parsed, null, 2);
        } catch (error) {
            return String(value);
        }
    }
    function showToast(message, type) {
        toast.textContent = message;
        toast.className = `toast show ${type === "error" ? "error" : ""}`;
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => {
            toast.className = "toast";
        }, 3200);
    }
    function newRequestId() {
        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return window.crypto.randomUUID();
        }
        return `req_${Date.now()}_${Math.random().toString(16).slice(2)}`;
    }
    function setLoading(button, loadingText) {
        if (!button) {
            return () => {};
        }
        const oldText = button.textContent;
        button.disabled = true;
        button.textContent = loadingText || "处理中...";
        return () => {
            button.disabled = false;
            button.textContent = oldText;
        };
    }
    async function guard(action, successMessage) {
        try {
            const result = await action();
            if (successMessage) {
                showToast(successMessage);
            }
            return result;
        } catch (error) {
            showToast(error.message || "操作失败", "error");
            throw error;
        }
    }
    function currentRoute() {
        return (location.hash || "#/diet").slice(1).split("?")[0] || "/diet";
    }
    function navigate(route) {
        location.hash = route;
    }
    function setActiveNav(route) {
        document.querySelectorAll("[data-nav]").forEach((item) => {
            item.classList.toggle("active", item.dataset.nav === route);
        });
    }
    function render() {
        if (!DietApi.isAuthenticated()) {
            openAuthPanel("login");
            return;
        }
        const route = currentRoute();
        setActiveNav(route);
        if (route === "/diet") {
            renderHome();
        } else if (route === "/diet/profile") {
            renderProfile();
        } else if (route === "/diet/checkins") {
            renderCheckins();
        } else if (route === "/diet/chat") {
            renderChat();
        } else if (route === "/diet/meals/personal") {
            renderPersonalMeals();
        } else if (route === "/diet/meals/public") {
            renderPublicMeals();
        } else if (route === "/admin/traces") {
            renderTraces();
        } else if (route === "/admin/evaluations") {
            renderEvaluations();
        } else {
            navigate("/diet");
        }
        app.focus({ preventScroll: true });
    }
    function renderHome() {
        app.innerHTML = `
            <section class="hero">
                <div class="hero-panel">
                    <span class="badge">多 Agent 饮食推荐</span>
                    <h1>用更轻松的方式决定今天吃什么</h1>
                    <p>维护你的个人餐食库，也可以从公共餐食库开始。助手会根据时间、心情、场景、健康目标、口味和便利程度给出推荐，并在信息不足时主动追问。</p>
                    <div class="hero-actions">
                        <a class="btn soft" href="#/diet/profile">完善健康档案</a>
                        <a class="btn primary" href="#/diet/chat">开始聊天推荐</a>
                        <a class="btn soft" href="#/diet/meals/personal">管理个人餐食</a>
                        <a class="btn ghost" href="#/admin/traces">查看 Trace</a>
                    </div>
                </div>
                <aside class="grid stats">
                    ${statCard("个人餐食", state.home.loaded ? state.home.personalCount : "加载中", "你的私有餐食库，用于个性化推荐")}
                    ${statCard("公共餐食", state.home.loaded ? state.home.publicCount : "加载中", "系统预置餐食，适合快速体验")}
                    ${statCard("当前用户", DietApi.getCurrentUser().displayName || DietApi.getCurrentUser().username, "数据将按当前登录账号隔离")}
                </aside>
            </section>
            <section class="grid three" style="margin-top: 18px;">
                ${featureCard("健康档案", "填写身高、体重、年龄、运动频率和目标，生成每日营养参考。", "#/diet/profile")}
                ${featureCard("饮食打卡", "上传餐食图片，核对菜品与估算营养后记录当天摄入。", "#/diet/checkins")}
                ${featureCard("聊天推荐", "按自然语言表达需求，页面会展示澄清问题、推荐卡片和反馈入口。", "#/diet/chat")}
                ${featureCard("餐食维护", "用标签多选维护自己的常吃餐食，后续推荐会优先从个人库检索。", "#/diet/meals/personal")}
                ${featureCard("评测后台", "查看请求 Trace，标注预期结果，并生成批量评估报告。", "#/admin/evaluations")}
            </section>
        `;
        loadHomeStats();
    }
    function statCard(label, value, desc) {
        return `
            <div class="stat-card">
                <span class="muted">${escapeHtml(label)}</span>
                <strong>${escapeHtml(value)}</strong>
                <p class="muted">${escapeHtml(desc)}</p>
            </div>
        `;
    }
    function featureCard(title, desc, href) {
        return `
            <article class="card">
                <div class="card-title">
                    <div>
                        <h3>${escapeHtml(title)}</h3>
                        <p>${escapeHtml(desc)}</p>
                    </div>
                </div>
                <a class="btn soft" href="${href}">进入</a>
            </article>
        `;
    }
    async function loadHomeStats() {
        if (state.home.loaded) {
            return;
        }
        try {
            const [personal, publicMeals] = await Promise.all([
                DietApi.listPersonalMeals(),
                DietApi.listPublicMeals()
            ]);
            state.home = {
                loaded: true,
                personalCount: personal.length,
                publicCount: publicMeals.length
            };
            if (currentRoute() === "/diet") {
                renderHome();
            }
        } catch (error) {
            showToast(error.message || "首页数据加载失败", "error");
        }
    }
    function renderChat() {
        app.innerHTML = `
            <section class="chat-layout">
                <div class="section chat-window">
                    <div class="card-title">
                        <div>
                            <h2>聊天推荐</h2>
                            <p>当前会话：${state.chat.sessionId ? escapeHtml(state.chat.sessionId) : "尚未创建，发送消息时自动创建"}</p>
                        </div>
                        <div class="inline-actions">
                            <button class="btn ${state.chat.sourceMode === "PERSONAL" ? "soft" : "ghost"}" data-action="set-source" data-source="PERSONAL">个人库</button>
                            <button class="btn ${state.chat.sourceMode === "PUBLIC" ? "soft" : "ghost"}" data-action="set-source" data-source="PUBLIC">公共库</button>
                            <button class="btn ghost" data-action="new-session">新会话</button>
                        </div>
                    </div>
                    <div id="messages" class="messages">${state.chat.messages.map(renderMessage).join("")}</div>
                    <form id="chatForm" class="composer">
                        <textarea name="message" placeholder="例如：今晚想吃清淡一点，最好快手一点" required></textarea>
                        <button class="btn primary" type="submit">${state.chat.sending ? "发送中..." : "发送"}</button>
                    </form>
                </div>
                <aside class="grid">
                    <div class="card">
                        <div class="card-title">
                            <div>
                                <h3>快捷问题</h3>
                                <p>点击后可直接填入输入框。</p>
                            </div>
                        </div>
                        <div class="chips">
                            ${["早餐想吃方便一点", "午餐不超过600大卡，蛋白质至少30g，不吃花生", "晚饭推荐清淡低脂的", "今天心情一般，想吃点热乎的", "换一批，不想吃刚才那些", "我胃不舒服，应该吃什么"].map((text) => `<button class="chip" data-action="quick-message" data-message="${escapeHtml(text)}">${escapeHtml(text)}</button>`).join("")}
                        </div>
                    </div>
                    <div class="card">
                        <h3>使用提示</h3>
                        <p class="muted">PERSONAL 模式依赖你的个人餐食库；如果还没有数据，可以先去维护餐食，或切换到 PUBLIC 模式体验。</p>
                        <div class="button-row">
                            <a class="btn soft" href="#/diet/meals/personal">维护餐食</a>
                            <a class="btn ghost" href="#/diet/meals/public">看公共库</a>
                        </div>
                    </div>
                </aside>
            </section>
        `;
        scrollMessagesToBottom();
    }
    function renderMessage(message) {
        const mealCards = (message.meals || []).map((meal) => renderMealCard(meal, { feedback: true, sessionId: message.sessionId })).join("");
        const missingSlots = message.missingSlots && message.missingSlots.length
            ? `<div class="chips">${message.missingSlots.map((slot) => `<span class="chip selected">${escapeHtml(SLOT_LABELS[slot] || slot)}</span>`).join("")}</div>`
            : "";
        const trace = message.traceId
            ? `<span>traceId：<a href="#/admin/traces" data-action="open-trace" data-trace-id="${escapeHtml(message.traceId)}">${escapeHtml(message.traceId)}</a></span>`
            : "";
        return `
            <article class="message ${message.role}">
                <div class="bubble">${escapeHtml(message.text)}</div>
                ${missingSlots}
                ${mealCards ? `<div class="grid">${mealCards}</div>` : ""}
                ${trace ? `<div class="message-meta">${trace}</div>` : ""}
            </article>
        `;
    }
    function scrollMessagesToBottom() {
        const messages = document.getElementById("messages");
        if (messages) {
            messages.scrollTop = messages.scrollHeight;
        }
    }
    async function submitChat(form) {
        const messageInput = form.elements.message;
        const message = messageInput.value.trim();
        if (!message || state.chat.sending) {
            return;
        }
        state.chat.messages.push({ role: "user", text: message });
        messageInput.value = "";
        state.chat.sending = true;
        // 网络层重试必须复用该值，服务端会直接返回本次已完成的响应，避免重复调用 LLM。
        const requestId = newRequestId();
        renderChat();
        try {
            if (!state.chat.sessionId) {
                const session = await DietApi.createSession();
                state.chat.sessionId = session.sessionId;
            }
            const response = await DietApi.chat({
                requestId,
                sessionId: state.chat.sessionId,
                message,
                sourceMode: state.chat.sourceMode,
                context: {}
            });
            state.chat.sessionId = response.sessionId || state.chat.sessionId;
            state.chat.messages.push({
                role: "assistant",
                text: response.clarifyQuestion || response.speechText || "我已经处理完这轮请求。",
                responseType: response.responseType,
                meals: response.displayBlocks || [],
                missingSlots: response.missingSlots || [],
                traceId: response.traceId,
                sessionId: response.sessionId || state.chat.sessionId
            });
        } catch (error) {
            showToast(error.message || "聊天请求失败", "error");
            state.chat.messages.push({ role: "assistant", text: "这轮请求失败了，请稍后重试。" });
        } finally {
            state.chat.sending = false;
            renderChat();
        }
    }
    function resetChat() {
        state.chat.sessionId = null;
        state.chat.messages = [
            {
                role: "assistant",
                text: "已开启新会话。告诉我你的用餐时间、口味、场景或健康目标，我来推荐。"
            }
        ];
        renderChat();
    }
    async function renderPersonalMeals() {
        if (!state.slotOptions) {
            app.innerHTML = `<section class="section"><div class="empty">标签字典加载中...</div></section>`;
            await ensureSlotOptions();
            if (currentRoute() !== "/diet/meals/personal") {
                return;
            }
        }
        await ensurePersonalMeals();
        if (currentRoute() !== "/diet/meals/personal") {
            return;
        }
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>个人餐食</h2>
                            <p>维护常吃餐食，聊天推荐时可切换到个人库。</p>
                        </div>
                        <button class="btn primary" data-action="new-meal">新增餐食</button>
                    </div>
                    <div id="personalMealList">${renderMealList(state.personalMeals, { editable: true })}</div>
                </div>
                <aside class="section">
                    ${renderMealForm()}
                </aside>
            </section>
        `;
    }
    function renderMealForm() {
        const meal = state.editingMeal || emptyMeal();
        const title = meal.id ? "编辑餐食" : "新增餐食";
        return `
            <div class="card-title">
                <div>
                    <h3>${title}</h3>
                    <p>从下拉框选择标签，用餐时间为必选项，其余可留空。</p>
                </div>
            </div>
            <form id="mealForm" class="form-grid">
                <input type="hidden" name="mealId" value="${escapeHtml(meal.id || "")}">
                <div class="field full">
                    <label for="mealName">餐食名称</label>
                    <input id="mealName" name="name" value="${escapeHtml(meal.name || "")}" placeholder="例如：番茄鸡蛋面" required>
                </div>
                <p class="field-hint full">标签下拉框支持多选：Windows 按住 Ctrl，Mac 按住 Command 点击可多项选择。</p>
                ${Object.entries(SLOT_LABELS).map(([key, label]) => renderSlotPicker(key, label, meal[key] || [])).join("")}
                ${renderNutritionFields(meal.nutrition || {})}
                <div class="field full">
                    <div class="button-row">
                        <button class="btn primary" type="submit">${meal.id ? "保存修改" : "创建餐食"}</button>
                        <button class="btn ghost" type="button" data-action="cancel-edit">清空</button>
                    </div>
                </div>
            </form>
        `;
    }
    function renderSlotPicker(key, label, selected) {
        const options = state.slotOptions && state.slotOptions[key] ? state.slotOptions[key] : [];
        const selectedSet = new Set(selected || []);
        const required = key === "mealTime";
        return `
            <div class="field">
                <label for="slot-${escapeHtml(key)}">${escapeHtml(label)}${required ? "（必选）" : ""}</label>
                <select
                    id="slot-${escapeHtml(key)}"
                    class="slot-select"
                    name="${escapeHtml(key)}"
                    multiple
                    size="5"
                    ${required ? "required" : ""}
                >
                    ${options.map((option) => {
                        const isSelected = selectedSet.has(option);
                        return `<option value="${escapeHtml(option)}" ${isSelected ? "selected" : ""}>${escapeHtml(option)}</option>`;
                    }).join("")}
                </select>
            </div>
        `;
    }
    async function renderProfile() {
        if (!state.profile.loaded) {
            app.innerHTML = `<section class="section"><div class="empty">正在加载你的健康档案...</div></section>`;
            await ensureProfile();
            if (currentRoute() !== "/diet/profile") {
                return;
            }
        }
        const profile = state.profile.data || { configured: false, diseaseHistory: [] };
        const target = profile.nutritionTarget;
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>我的健康档案</h2>
                            <p>用于估算日常能量和三大营养素目标，并作为推荐助手的整日饮食背景。</p>
                        </div>
                        <span class="badge">${profile.configured ? "已生成目标" : "待完善"}</span>
                    </div>
                    ${renderProfileForm(profile)}
                </div>
                <aside class="section">
                    ${renderNutritionTarget(target, profile.medicalDisclaimer)}
                </aside>
            </section>
        `;
    }
    function renderProfileForm(profile) {
        const activityLevel = profile.activityLevel || "MODERATE";
        const profileGoal = profile.profileGoal || "MAINTAIN";
        return `
            <form id="profileForm" class="form-grid">
                <div class="field">
                    <label for="heightCm">身高（cm）</label>
                    <input id="heightCm" type="number" name="heightCm" min="80" max="250" step="0.1" value="${escapeHtml(profile.heightCm || "")}" required>
                </div>
                <div class="field">
                    <label for="weightKg">体重（kg）</label>
                    <input id="weightKg" type="number" name="weightKg" min="25" max="500" step="0.1" value="${escapeHtml(profile.weightKg || "")}" required>
                </div>
                <div class="field">
                    <label for="profileAge">年龄</label>
                    <input id="profileAge" type="number" name="age" min="14" max="120" step="1" value="${escapeHtml(profile.age || "")}" required>
                </div>
                <div class="field">
                    <label for="activityLevel">运动频率</label>
                    <select id="activityLevel" name="activityLevel" required>
                        ${profileSelectOption("SEDENTARY", "久坐（几乎不运动）", activityLevel)}
                        ${profileSelectOption("LIGHT", "轻度（每周 1–3 次）", activityLevel)}
                        ${profileSelectOption("MODERATE", "中等（每周 3–5 次）", activityLevel)}
                        ${profileSelectOption("HIGH", "较高（每周 6–7 次）", activityLevel)}
                        ${profileSelectOption("ATHLETE", "高强度 / 体力劳动", activityLevel)}
                    </select>
                </div>
                <div class="field">
                    <label for="profileGoal">健康目标</label>
                    <select id="profileGoal" name="profileGoal" required>
                        ${profileSelectOption("FAT_LOSS", "减脂", profileGoal)}
                        ${profileSelectOption("MAINTAIN", "维持体重", profileGoal)}
                        ${profileSelectOption("MUSCLE_GAIN", "增肌", profileGoal)}
                    </select>
                </div>
                <div class="field full">
                    <label for="diseaseHistory">疾病史（可选）</label>
                    <input id="diseaseHistory" name="diseaseHistory" value="${escapeHtml((profile.diseaseHistory || []).join("，"))}" placeholder="例如：高血压，糖尿病；多个项目用逗号分隔">
                    <span>仅用于提示医疗风险；涉及疾病、用药或特殊人群，请以医生或注册营养师建议为准。</span>
                </div>
                <div class="field full">
                    <button class="btn primary" type="submit">${profile.configured ? "更新并重新计算" : "生成个性化营养目标"}</button>
                </div>
            </form>
        `;
    }
    function profileSelectOption(value, label, selectedValue) {
        return `<option value="${value}" ${value === selectedValue ? "selected" : ""}>${label}</option>`;
    }
    function renderNutritionTarget(target, disclaimer) {
        if (!target) {
            return `
                <div class="card-title"><div><h3>每日营养目标</h3><p>完善左侧档案后自动生成。</p></div></div>
                <div class="empty">这里会展示每日能量、蛋白质、脂肪和碳水化合物参考。</div>
                <p class="muted">${escapeHtml(disclaimer || "该结果仅用于日常饮食参考。")}</p>
            `;
        }
        return `
            <div class="card-title"><div><h3>每日营养目标</h3><p>按当前档案估算的日常起始参考。</p></div></div>
            <div class="grid two nutrition-target-grid">
                ${nutritionTargetMetric("目标能量", `${formatNutritionValue(target.dailyEnergyKcal)} kcal`)}
                ${nutritionTargetMetric("维持能量", `${formatNutritionValue(target.maintenanceEnergyKcal)} kcal`)}
                ${nutritionTargetMetric("蛋白质", `${formatNutritionValue(target.dailyProteinG)} g`)}
                ${nutritionTargetMetric("脂肪", `${formatNutritionValue(target.dailyFatG)} g`)}
                ${nutritionTargetMetric("碳水化合物", `${formatNutritionValue(target.dailyCarbohydrateG)} g`)}
            </div>
            <div class="subtle-divider"></div>
            <p class="muted">${escapeHtml(target.calculationNote || "")}</p>
            <p class="muted">${escapeHtml(disclaimer || "该结果仅用于日常饮食参考。")}</p>
        `;
    }
    function nutritionTargetMetric(label, value) {
        return `<div class="stat-card nutrition-target-metric"><span class="muted">${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
    }
    function formatNutritionValue(value) {
        const numeric = Number(value);
        return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(1);
    }
    async function ensureProfile(force) {
        if (!force && state.profile.loaded) {
            return state.profile.data;
        }
        if (state.profile.loading) {
            return state.profile.data;
        }
        state.profile.loading = true;
        try {
            state.profile.data = await DietApi.getProfile();
            state.profile.loaded = true;
            return state.profile.data;
        } catch (error) {
            showToast(error.message || "健康档案加载失败", "error");
            return null;
        } finally {
            state.profile.loading = false;
        }
    }
    async function saveProfile(form) {
        const formData = new FormData(form);
        const diseaseHistory = String(formData.get("diseaseHistory") || "")
            .split(/[,，、]/)
            .map((value) => value.trim())
            .filter(Boolean);
        const payload = {
            heightCm: Number(formData.get("heightCm")),
            weightKg: Number(formData.get("weightKg")),
            age: Number(formData.get("age")),
            activityLevel: String(formData.get("activityLevel") || ""),
            diseaseHistory,
            profileGoal: String(formData.get("profileGoal") || "")
        };
        const restore = setLoading(form.querySelector("button[type=submit]"), "计算中...");
        try {
            state.profile.data = await guard(() => DietApi.updateProfile(payload), "营养目标已更新");
            state.profile.loaded = true;
            renderProfile();
        } finally {
            restore();
        }
    }
    async function renderCheckins() {
        if (!state.checkins.loaded) {
            app.innerHTML = `<section class="section"><div class="empty">正在加载当天饮食打卡...</div></section>`;
            await ensureCheckinSummary();
            if (currentRoute() !== "/diet/checkins") {
                return;
            }
        }
        const summary = state.checkins.summary || emptyCheckinSummary();
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>图片饮食打卡</h2>
                            <p>上传餐食图片后核对菜品、份量和营养估算；确认前不会写入当天摄入记录。</p>
                        </div>
                        <span class="badge">识别后需确认</span>
                    </div>
                    <form id="checkinImageForm" class="form-grid">
                        <div class="field full">
                            <label for="checkinImage">餐食图片</label>
                            <input id="checkinImage" type="file" name="image" accept="image/jpeg,image/png,image/webp" required>
                            <span>支持 JPG、PNG、WEBP，最大 5MB。油盐、酱料和实际份量难以从图片准确判断，请在下一步核对。</span>
                        </div>
                        <div class="field full">
                            <button class="btn primary" type="submit" ${state.checkins.uploading ? "disabled" : ""}>${state.checkins.uploading ? "识别中..." : "上传并识别"}</button>
                        </div>
                    </form>
                    ${renderRecognitionDraft(state.checkins.recognition)}
                </div>
                <aside class="section">
                    ${renderCheckinSummary(summary)}
                </aside>
            </section>
        `;
    }
    function emptyCheckinSummary() {
        return {
            date: state.checkins.date,
            checkins: [],
            consumed: { energyKcal: 0, proteinG: 0, fatG: 0, carbohydrateG: 0 },
            nutritionTarget: null,
            remaining: null,
            message: "正在加载当天汇总。"
        };
    }
    function renderRecognitionDraft(recognition) {
        if (!recognition) {
            return "";
        }
        const items = recognition.items || [];
        const preview = state.checkins.previewUrl
            ? `<img class="checkin-image-preview" src="${state.checkins.previewUrl}" alt="待识别的餐食图片">`
            : "";
        return `
            <div class="subtle-divider"></div>
            <div class="card-title">
                <div>
                    <h3>${recognition.automated ? "识别结果，请确认" : "请手动补充菜品"}</h3>
                    <p>${escapeHtml(recognition.message || "请核对后确认打卡。")}</p>
                </div>
                <button class="btn soft" type="button" data-action="add-checkin-item">新增菜品</button>
            </div>
            ${preview}
            <form id="checkinConfirmForm" class="form-grid">
                <div class="field">
                    <label>打卡日期</label>
                    <input type="date" name="checkinDate" max="${dateInputValue(new Date())}" min="${dateInputValue(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000))}" value="${escapeHtml(state.checkins.date)}" required>
                </div>
                <div class="field">
                    <label>用餐时段</label>
                    <select name="mealTime" required>
                        <option value="早餐">早餐</option>
                        <option value="午餐" selected>午餐</option>
                        <option value="晚餐">晚餐</option>
                        <option value="加餐">加餐</option>
                    </select>
                </div>
                <input type="hidden" name="recognitionId" value="${escapeHtml(recognition.recognitionId)}">
                <div class="field full">
                    <span>每项营养均为单份估算。若图片识别不完整，请修正数值或添加菜品。</span>
                </div>
                <div class="field full checkin-item-list">
                    ${items.length ? items.map((item, index) => renderCheckinItemEditor(item, index)).join("") : `<div class="empty">尚未识别到可靠菜品，点击“新增菜品”后手动填写。</div>`}
                </div>
                <div class="field full">
                    <button class="btn primary" type="submit" ${items.length ? "" : "disabled"}>确认并保存打卡</button>
                </div>
            </form>
        `;
    }
    function renderCheckinItemEditor(item, index) {
        return `
            <article class="card checkin-item-editor" data-checkin-item>
                <div class="card-title">
                    <div><h3>菜品 ${index + 1}</h3><p>${item.confidence === null || item.confidence === undefined ? "手动填写" : `识别置信度：${Math.round(Number(item.confidence) * 100)}%`}</p></div>
                    <button class="btn ghost" type="button" data-action="remove-checkin-item" data-index="${index}">移除</button>
                </div>
                <div class="form-grid">
                    <div class="field full"><label>菜品名称</label><input name="name" value="${escapeHtml(item.name || "")}" required></div>
                    ${checkinNumberInput("estimatedWeightG", "估算份量（g）", item.estimatedWeightG, 0, 3000)}
                    ${checkinNumberInput("energyKcal", "热量（kcal）", item.energyKcal, 0, 20000)}
                    ${checkinNumberInput("proteinG", "蛋白质（g）", item.proteinG, 0, 2000)}
                    ${checkinNumberInput("fatG", "脂肪（g）", item.fatG, 0, 2000)}
                    ${checkinNumberInput("carbohydrateG", "碳水化合物（g）", item.carbohydrateG, 0, 3000)}
                    <input type="hidden" name="confidence" value="${escapeHtml(item.confidence ?? 0)}">
                    <input type="hidden" name="nutritionSource" value="${escapeHtml(item.nutritionSource || "USER_CONFIRMED")}">
                </div>
            </article>
        `;
    }
    function checkinNumberInput(name, label, value, min, max) {
        return `<div class="field"><label>${label}</label><input type="number" name="${name}" min="${min}" max="${max}" step="0.1" value="${escapeHtml(value ?? "")}" required></div>`;
    }
    function renderCheckinSummary(summary) {
        const consumed = summary.consumed || emptyCheckinSummary().consumed;
        const target = summary.nutritionTarget;
        const remaining = summary.remaining;
        return `
            <div class="card-title">
                <div><h3>当日摄入</h3><p>查看已打卡餐食与每日目标的差距。</p></div>
            </div>
            <form id="checkinDateForm" class="toolbar">
                <input type="date" name="date" value="${escapeHtml(state.checkins.date)}" max="${dateInputValue(new Date())}">
                <button class="btn soft" type="submit">查看日期</button>
            </form>
            <div class="grid two nutrition-target-grid" style="margin-top: 14px;">
                ${nutritionTargetMetric("已摄入热量", `${formatNutritionValue(consumed.energyKcal)} kcal`)}
                ${nutritionTargetMetric("已摄入蛋白质", `${formatNutritionValue(consumed.proteinG)} g`)}
                ${nutritionTargetMetric("已摄入脂肪", `${formatNutritionValue(consumed.fatG)} g`)}
                ${nutritionTargetMetric("已摄入碳水", `${formatNutritionValue(consumed.carbohydrateG)} g`)}
            </div>
            ${target && remaining ? `
                <div class="subtle-divider"></div>
                <p class="muted">每日目标 ${formatNutritionValue(target.dailyEnergyKcal)} kcal；剩余热量 ${formatNutritionValue(remaining.energyKcal)} kcal，蛋白质 ${formatNutritionValue(remaining.proteinG)} g。</p>
            ` : ""}
            <p class="muted">${escapeHtml(summary.message || "")}</p>
            <div class="subtle-divider"></div>
            <h3>已保存记录</h3>
            <div class="checkin-history">
                ${(summary.checkins || []).length ? summary.checkins.map(renderCheckinHistoryItem).join("") : `<div class="empty">这一天还没有饮食打卡。</div>`}
            </div>
        `;
    }
    function renderCheckinHistoryItem(checkin) {
        const items = (checkin.items || []).map((item) => escapeHtml(item.name)).join("、") || "未命名菜品";
        return `
            <article class="checkin-history-item">
                <div><strong>${escapeHtml(checkin.mealTime)}</strong><p class="muted">${items}</p><span>${formatNutritionValue(checkin.totals.energyKcal)} kcal · 蛋白质 ${formatNutritionValue(checkin.totals.proteinG)} g</span></div>
                <div class="button-row">
                    <button class="btn soft" type="button" data-action="view-checkin-image" data-id="${escapeHtml(checkin.id)}">查看图片</button>
                    <button class="btn ghost" type="button" data-action="delete-checkin" data-id="${escapeHtml(checkin.id)}">删除</button>
                </div>
            </article>
        `;
    }
    async function ensureCheckinSummary(force) {
        if (!force && state.checkins.loaded) {
            return state.checkins.summary;
        }
        if (state.checkins.loading) {
            return state.checkins.summary;
        }
        state.checkins.loading = true;
        try {
            state.checkins.summary = await DietApi.getCheckinSummary(state.checkins.date);
            state.checkins.loaded = true;
            return state.checkins.summary;
        } catch (error) {
            showToast(error.message || "饮食打卡加载失败", "error");
            return null;
        } finally {
            state.checkins.loading = false;
        }
    }
    async function recognizeCheckinImage(form) {
        const image = form.elements.image.files[0];
        if (!image || state.checkins.uploading) {
            return;
        }
        state.checkins.uploading = true;
        renderCheckins();
        try {
            const recognition = await DietApi.recognizeCheckinImage(image);
            revokeCheckinPreview();
            state.checkins.previewUrl = URL.createObjectURL(image);
            state.checkins.recognition = { ...recognition, items: recognition.items || [] };
            showToast(recognition.automated ? "图片已识别，请核对后保存" : "图片已上传，请手动补充菜品");
        } catch (error) {
            showToast(error.message || "图片识别失败", "error");
        } finally {
            state.checkins.uploading = false;
            renderCheckins();
        }
    }
    function addCheckinItem() {
        if (!state.checkins.recognition) {
            return;
        }
        state.checkins.recognition.items.push({
            name: "",
            estimatedWeightG: null,
            energyKcal: null,
            proteinG: null,
            fatG: null,
            carbohydrateG: null,
            confidence: null,
            nutritionSource: "USER_CONFIRMED"
        });
        renderCheckins();
    }
    function removeCheckinItem(index) {
        if (!state.checkins.recognition) {
            return;
        }
        state.checkins.recognition.items.splice(Number(index), 1);
        renderCheckins();
    }
    async function saveCheckin(form) {
        const payload = {
            recognitionId: String(new FormData(form).get("recognitionId") || ""),
            checkinDate: String(new FormData(form).get("checkinDate") || ""),
            mealTime: String(new FormData(form).get("mealTime") || ""),
            items: Array.from(form.querySelectorAll("[data-checkin-item]")).map((element) => ({
                name: String(element.querySelector("[name=name]").value || "").trim(),
                estimatedWeightG: Number(element.querySelector("[name=estimatedWeightG]").value),
                energyKcal: Number(element.querySelector("[name=energyKcal]").value),
                proteinG: Number(element.querySelector("[name=proteinG]").value),
                fatG: Number(element.querySelector("[name=fatG]").value),
                carbohydrateG: Number(element.querySelector("[name=carbohydrateG]").value),
                confidence: Number(element.querySelector("[name=confidence]").value),
                nutritionSource: String(element.querySelector("[name=nutritionSource]").value || "USER_CONFIRMED")
            }))
        };
        const restore = setLoading(form.querySelector("button[type=submit]"), "保存中...");
        try {
            await DietApi.saveCheckin(payload);
            state.checkins.date = payload.checkinDate;
            state.checkins.recognition = null;
            revokeCheckinPreview();
            await ensureCheckinSummary(true);
            showToast("饮食打卡已保存");
            renderCheckins();
        } catch (error) {
            showToast(error.message || "保存饮食打卡失败", "error");
        } finally {
            restore();
        }
    }
    async function changeCheckinDate(form) {
        state.checkins.date = String(new FormData(form).get("date") || dateInputValue(new Date()));
        state.checkins.loaded = false;
        await renderCheckins();
    }
    async function deleteCheckin(id) {
        if (!window.confirm("确定删除这条饮食打卡吗？")) {
            return;
        }
        try {
            await DietApi.deleteCheckin(id);
            await ensureCheckinSummary(true);
            showToast("饮食打卡已删除");
            renderCheckins();
        } catch (error) {
            showToast(error.message || "删除饮食打卡失败", "error");
        }
    }
    async function viewCheckinImage(id) {
        try {
            const blob = await DietApi.getCheckinImage(id);
            const objectUrl = URL.createObjectURL(blob);
            window.open(objectUrl, "_blank", "noopener");
            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60 * 1000);
        } catch (error) {
            showToast(error.message || "读取餐食图片失败", "error");
        }
    }
    function revokeCheckinPreview() {
        if (state.checkins.previewUrl) {
            URL.revokeObjectURL(state.checkins.previewUrl);
            state.checkins.previewUrl = null;
        }
    }
    function renderNutritionFields(nutrition) {
        const data = nutrition || {};
        const allergens = Array.isArray(data.allergens) ? data.allergens.join("，") : "";
        return [
            '<section class="field full nutrition-fields">',
            '<label>营养信息与过敏原（可选）</label>',
            '<p class="field-hint">每份餐食的估算值；填写后可参与热量、蛋白质与过敏原硬约束筛选。</p>',
            '<div class="form-grid">',
            nutritionInput("energyKcal", "热量（kcal）", data.energyKcal, "0.1"),
            nutritionInput("proteinG", "蛋白质（g）", data.proteinG, "0.1"),
            nutritionInput("fatG", "脂肪（g）", data.fatG, "0.1"),
            nutritionInput("carbohydrateG", "碳水（g）", data.carbohydrateG, "0.1"),
            nutritionInput("fiberG", "膳食纤维（g）", data.fiberG, "0.1"),
            nutritionInput("sodiumMg", "钠（mg）", data.sodiumMg, "1"),
            '</div>',
            '<div class="field full"><label>过敏原（用逗号分隔）</label><input name="allergens" value="' + escapeHtml(allergens) + '" placeholder="例如：花生，乳制品"></div>',
            '<div class="field full"><label>营养数据来源</label><input name="nutritionSource" value="' + escapeHtml(data.nutritionSource || "") + '" placeholder="例如：包装标签、食堂菜单、人工估算"></div>',
            '</section>'
        ].join("");
    }
    function nutritionInput(name, label, value, step) {
        const displayValue = value === null || value === undefined ? "" : value;
        return '<div class="field"><label>' + escapeHtml(label) + '</label><input name="' + name + '" type="number" min="0" step="' + step + '" value="' + escapeHtml(displayValue) + '"></div>';
    }
    function emptyMeal() {
        return {
            name: "",
            mealTime: [],
            mood: [],
            scene: [],
            healthGoal: [],
            cuisine: [],
            taste: [],
            convenience: [],
            nutrition: {
                energyKcal: null, proteinG: null, fatG: null, carbohydrateG: null,
                fiberG: null, sodiumMg: null, allergens: [], nutritionSource: null
            }
        };
    }
    function renderMealList(meals, options) {
        if (!meals.length) {
            return `<div class="empty">暂无餐食。可以先新增几道常吃的菜。</div>`;
        }
        return `<div class="grid two">${meals.map((meal) => renderMealCard(meal, options || {})).join("")}</div>`;
    }
    function renderMealCard(meal, options) {
        const editable = options && options.editable;
        const feedback = options && options.feedback;
        return `
            <article class="meal-card">
                <header>
                    <div>
                        <h3>${escapeHtml(meal.name)}</h3>
                        <p class="muted">${escapeHtml(meal.sourceType || "")}</p>
                    </div>
                    ${meal.matchScore ? `<span class="score">匹配 ${Math.round(meal.matchScore * 100)}%</span>` : ""}
                </header>
                <div class="chips">${mealTags(meal).map((tag) => `<span class="chip selected">${escapeHtml(tag)}</span>`).join("")}</div>
                ${renderNutrition(meal.nutrition)}
                ${editable ? `
                    <div class="button-row">
                        <button class="btn soft" data-action="edit-meal" data-id="${escapeHtml(meal.id)}">编辑</button>
                        <button class="btn ghost" data-action="delete-meal" data-id="${escapeHtml(meal.id)}">删除</button>
                    </div>
                ` : ""}
                ${feedback ? `
                    <div class="button-row">
                        <button class="btn soft" data-action="feedback" data-action-value="LIKE" data-item-id="${escapeHtml(meal.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">喜欢</button>
                        <button class="btn ghost" data-action="feedback" data-action-value="SELECT" data-item-id="${escapeHtml(meal.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">采纳</button>
                        <button class="btn ghost" data-action="feedback" data-action-value="DISLIKE" data-item-id="${escapeHtml(meal.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">不合适</button>
                    </div>
                ` : ""}
            </article>
        `;
    }
    function mealTags(meal) {
        return Object.keys(SLOT_LABELS).flatMap((key) => (meal[key] || []).map((value) => `${SLOT_LABELS[key]}：${value}`));
    }
    function renderNutrition(nutrition) {
        if (!nutrition) {
            return "";
        }
        const details = [];
        if (nutrition.energyKcal !== null && nutrition.energyKcal !== undefined) details.push("热量 " + nutrition.energyKcal + " kcal");
        if (nutrition.proteinG !== null && nutrition.proteinG !== undefined) details.push("蛋白质 " + nutrition.proteinG + " g");
        if (nutrition.fatG !== null && nutrition.fatG !== undefined) details.push("脂肪 " + nutrition.fatG + " g");
        if (nutrition.carbohydrateG !== null && nutrition.carbohydrateG !== undefined) details.push("碳水 " + nutrition.carbohydrateG + " g");
        if (Array.isArray(nutrition.allergens) && nutrition.allergens.length) details.push("过敏原：" + nutrition.allergens.join("、"));
        return details.length ? '<p class="field-hint nutrition-summary">' + escapeHtml(details.join(" · ")) + '</p>' : "";
    }
    async function ensurePersonalMeals(force) {
        if (!force && state.personalMeals.length) {
            return;
        }
        try {
            state.personalMeals = await DietApi.listPersonalMeals();
            state.home.loaded = false;
            if (currentRoute() === "/diet/meals/personal") {
                document.getElementById("personalMealList").innerHTML = renderMealList(state.personalMeals, { editable: true });
            }
        } catch (error) {
            showToast(error.message || "个人餐食加载失败", "error");
        }
    }
    async function ensureSlotOptions() {
        if (state.slotOptions) {
            return;
        }
        try {
            state.slotOptions = await DietApi.slotOptions();
        } catch (error) {
            showToast(error.message || "槽位字典加载失败", "error");
            throw error;
        }
    }
    async function saveMeal(form) {
        const { id, payload } = mealPayloadFromForm(form);
        if (!payload.name) {
            showToast("请填写餐食名称", "error");
            return;
        }
        if (!payload.mealTime.length) {
            showToast("请至少选择一个用餐时间标签", "error");
            return;
        }
        const restore = setLoading(form.querySelector("button[type=submit]"), "保存中...");
        try {
            await guard(async () => {
                if (id) {
                    return DietApi.updatePersonalMeal(id, payload);
                }
                return DietApi.createPersonalMeal(payload);
            }, id ? "餐食已更新" : "餐食已创建");
            state.editingMeal = null;
            await ensurePersonalMeals(true);
            renderPersonalMeals();
        } finally {
            restore();
        }
    }
    function mealPayloadFromForm(form) {
        const formData = new FormData(form);
        const payload = {
            name: String(formData.get("name") || "").trim()
        };
        Object.keys(SLOT_LABELS).forEach((key) => {
            payload[key] = formData.getAll(key).filter(Boolean);
        });
        payload.nutrition = {
            energyKcal: numberOrNull(formData.get("energyKcal")),
            proteinG: numberOrNull(formData.get("proteinG")),
            fatG: numberOrNull(formData.get("fatG")),
            carbohydrateG: numberOrNull(formData.get("carbohydrateG")),
            fiberG: numberOrNull(formData.get("fiberG")),
            sodiumMg: numberOrNull(formData.get("sodiumMg")),
            allergens: String(formData.get("allergens") || "").split(/[,，、]/).map((value) => value.trim()).filter(Boolean),
            nutritionSource: String(formData.get("nutritionSource") || "").trim() || null
        };
        return {
            id: String(formData.get("mealId") || "").trim(),
            payload
        };
    }
    function defaultCheckinState() {
        return {
            date: dateInputValue(new Date()),
            loaded: false,
            loading: false,
            uploading: false,
            summary: null,
            recognition: null,
            previewUrl: null
        };
    }
    function dateInputValue(date) {
        const pad = (value) => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }
    function numberOrNull(value) {
        const text = String(value || "").trim();
        return text === "" ? null : Number(text);
    }
    function editMeal(id) {
        const meal = state.personalMeals.find((item) => String(item.id) === String(id));
        if (!meal) {
            showToast("没有找到要编辑的餐食", "error");
            return;
        }
        state.editingMeal = JSON.parse(JSON.stringify(meal));
        renderPersonalMeals();
    }
    async function deleteMeal(id) {
        const meal = state.personalMeals.find((item) => String(item.id) === String(id));
        if (!meal || !window.confirm(`确定删除“${meal.name}”？`)) {
            return;
        }
        await guard(async () => {
            await DietApi.deletePersonalMeal(id);
            await ensurePersonalMeals(true);
            renderPersonalMeals();
        }, "餐食已删除");
    }
    function renderPublicMeals() {
        app.innerHTML = `
            <section class="section">
                <div class="card-title">
                    <div>
                        <h2>公共餐食</h2>
                        <p>系统预置餐食库，只读展示，可在聊天页切换到 PUBLIC 模式体验。</p>
                    </div>
                    <a class="btn primary" href="#/diet/chat">去聊天推荐</a>
                </div>
                <div id="publicMealList">${renderMealList(state.publicMeals, {})}</div>
            </section>
        `;
        ensurePublicMeals();
    }
    async function ensurePublicMeals(force) {
        if (!force && state.publicMeals.length) {
            return;
        }
        try {
            state.publicMeals = await DietApi.listPublicMeals();
            state.home.loaded = false;
            if (currentRoute() === "/diet/meals/public") {
                document.getElementById("publicMealList").innerHTML = renderMealList(state.publicMeals, {});
            }
        } catch (error) {
            showToast(error.message || "公共餐食加载失败", "error");
        }
    }
    function renderTraces() {
        const selected = state.traces.selected;
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>Trace 调试</h2>
                            <p>按时间范围或会话查询请求链路，查看意图修正、槽位和推荐事件。</p>
                        </div>
                    </div>
                    <form id="traceFilterForm" class="form-grid">
                        <div class="field">
                            <label>开始时间</label>
                            <input type="datetime-local" name="startAt" value="${escapeHtml(state.traces.filters.startAt)}" required>
                        </div>
                        <div class="field">
                            <label>结束时间</label>
                            <input type="datetime-local" name="endAt" value="${escapeHtml(state.traces.filters.endAt)}" required>
                        </div>
                        <div class="field">
                            <label>会话 ID（可选）</label>
                            <input name="sessionId" value="${escapeHtml(state.traces.filters.sessionId)}" placeholder="填写后按会话查询">
                        </div>
                        <div class="field">
                            <label>数量上限</label>
                            <input type="number" min="1" max="500" name="limit" value="${escapeHtml(state.traces.filters.limit)}">
                        </div>
                        <div class="field">
                            <label>标注状态</label>
                            <select name="onlyUnlabeled">
                                <option value="false" ${!state.traces.filters.onlyUnlabeled ? "selected" : ""}>全部</option>
                                <option value="true" ${state.traces.filters.onlyUnlabeled ? "selected" : ""}>仅未标注</option>
                            </select>
                        </div>
                        <div class="field">
                            <span>&nbsp;</span>
                            <button class="btn primary" type="submit">${state.traces.loading ? "查询中..." : "查询 Trace"}</button>
                        </div>
                    </form>
                    <div class="subtle-divider"></div>
                    ${renderTraceTable()}
                </div>
                <aside class="section">
                    ${selected ? renderTraceDetail(selected) : `<div class="empty">选择一条 Trace 查看详情和标注表单。</div>`}
                </aside>
            </section>
        `;
    }
    function renderTraceTable() {
        if (!state.traces.rows.length) {
            return `<div class="empty">暂无 Trace 数据。可以先在聊天页发起几轮对话。</div>`;
        }
        return `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Trace ID</th>
                            <th>会话</th>
                            <th>状态</th>
                            <th>事件</th>
                            <th>耗时</th>
                            <th>创建时间</th>
                            <th>标注</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${state.traces.rows.map((row) => `
                            <tr>
                                <td>${escapeHtml(row.traceId)}</td>
                                <td>${escapeHtml(row.sessionId)}</td>
                                <td>${escapeHtml(row.status || "-")}</td>
                                <td>${escapeHtml(row.eventCount ?? "-")}</td>
                                <td>${row.durationMs ? `${escapeHtml(row.durationMs)} ms` : "-"}</td>
                                <td>${escapeHtml(row.createdAt || "-")}</td>
                                <td>${row.expectedIntent ? `<span class="badge">${escapeHtml(row.expectedIntent)}</span>` : "<span class=\"muted\">未标注</span>"}</td>
                                <td><button class="btn soft" data-action="select-trace" data-trace-id="${escapeHtml(row.traceId)}">查看</button></td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
    function renderTraceDetail(trace) {
        return `
            <div class="card-title">
                <div>
                    <h3>Trace 详情</h3>
                    <p>${escapeHtml(trace.traceId)}</p>
                </div>
            </div>
            <div class="grid">
                <div>
                    <span class="badge">${escapeHtml(trace.status || "UNKNOWN")}</span>
                    <p class="muted">Session：${escapeHtml(trace.sessionId || "-")} · Events：${escapeHtml(trace.eventCount ?? "-")} · Duration：${escapeHtml(trace.durationMs ?? "-")} ms</p>
                </div>
                <details open>
                    <summary>Trace JSON</summary>
                    <pre class="json-box">${escapeHtml(safeJson(trace.traceJson))}</pre>
                </details>
                <form id="traceLabelForm" class="form-grid">
                    <input type="hidden" name="traceId" value="${escapeHtml(trace.traceId)}">
                    <div class="field">
                        <label>预期意图</label>
                        <select name="expectedIntent">
                            <option value="">不标注</option>
                            ${INTENTS.map((intent) => `<option value="${intent}" ${trace.expectedIntent === intent ? "selected" : ""}>${intent}</option>`).join("")}
                        </select>
                    </div>
                    <div class="field">
                        <label>澄清动作</label>
                        <select name="expectedClarifyAction">
                            <option value="">不标注</option>
                            <option value="ASK" ${trace.expectedClarifyAction === "ASK" ? "selected" : ""}>ASK</option>
                            <option value="READY" ${trace.expectedClarifyAction === "READY" ? "selected" : ""}>READY</option>
                        </select>
                    </div>
                    <div class="field full">
                        <label>预期槽位 JSON</label>
                        <textarea name="expectedSlots" placeholder='{"mealTime":["晚餐"],"taste":["清淡"]}'>${escapeHtml(safeJson(trace.expectedSlots))}</textarea>
                    </div>
                    <div class="field full">
                        <label>备注</label>
                        <textarea name="labelNote" placeholder="标注说明">${escapeHtml(trace.labelNote || "")}</textarea>
                    </div>
                    <div class="field full">
                        <button class="btn primary" type="submit">保存标注</button>
                    </div>
                </form>
            </div>
        `;
    }
    async function searchTraces(form) {
        const formData = new FormData(form);
        state.traces.filters = {
            startAt: formData.get("startAt"),
            endAt: formData.get("endAt"),
            sessionId: formData.get("sessionId").trim(),
            onlyUnlabeled: formData.get("onlyUnlabeled") === "true",
            limit: Number(formData.get("limit") || 50)
        };
        state.traces.loading = true;
        renderTraces();
        try {
            if (state.traces.filters.sessionId) {
                state.traces.rows = await DietApi.listSessionTraces(state.traces.filters.sessionId, state.traces.filters.limit);
            } else {
                state.traces.rows = await DietApi.listTraces({
                    startAt: state.traces.filters.startAt,
                    endAt: state.traces.filters.endAt,
                    onlyUnlabeled: state.traces.filters.onlyUnlabeled,
                    limit: state.traces.filters.limit
                });
            }
            state.traces.selected = state.traces.rows[0] || null;
        } catch (error) {
            showToast(error.message || "Trace 查询失败", "error");
        } finally {
            state.traces.loading = false;
            renderTraces();
        }
    }
    async function selectTrace(traceId) {
        await guard(async () => {
            state.traces.selected = await DietApi.getTrace(traceId);
            renderTraces();
        });
    }
    async function saveTraceLabel(form) {
        const formData = new FormData(form);
        const traceId = formData.get("traceId");
        const slotsText = formData.get("expectedSlots").trim();
        let expectedSlots = null;
        if (slotsText) {
            try {
                expectedSlots = JSON.parse(slotsText);
            } catch (error) {
                showToast("预期槽位必须是合法 JSON", "error");
                return;
            }
        }
        const payload = {
            expectedIntent: formData.get("expectedIntent") || null,
            expectedSlots,
            expectedClarifyAction: formData.get("expectedClarifyAction") || null,
            labelNote: formData.get("labelNote").trim()
        };
        await guard(async () => {
            await DietApi.labelTrace(traceId, payload);
            state.traces.selected = await DietApi.getTrace(traceId);
            const index = state.traces.rows.findIndex((row) => row.traceId === traceId);
            if (index >= 0) {
                state.traces.rows[index] = state.traces.selected;
            }
            renderTraces();
        }, "Trace 标注已保存");
    }
    function renderEvaluations() {
        app.innerHTML = `
            <section class="section">
                <div class="card-title">
                    <div>
                        <h2>评估报告</h2>
                        <p>基于已落库 Trace 生成规则评分、可选 LLM Judge 和反馈归因指标。</p>
                    </div>
                </div>
                <form id="evaluationForm" class="form-grid">
                    <div class="field">
                        <label>开始时间</label>
                        <input type="datetime-local" name="startAt" value="${escapeHtml(state.evaluation.form.startAt)}" required>
                    </div>
                    <div class="field">
                        <label>结束时间</label>
                        <input type="datetime-local" name="endAt" value="${escapeHtml(state.evaluation.form.endAt)}" required>
                    </div>
                    <div class="field">
                        <label>数量上限</label>
                        <input type="number" min="1" max="500" name="limit" value="${escapeHtml(state.evaluation.form.limit)}">
                    </div>
                    <div class="field">
                        <label>LLM Judge</label>
                        <select name="includeLlmJudge">
                            <option value="false" ${!state.evaluation.form.includeLlmJudge ? "selected" : ""}>关闭</option>
                            <option value="true" ${state.evaluation.form.includeLlmJudge ? "selected" : ""}>开启</option>
                        </select>
                    </div>
                    <div class="field full">
                        <button class="btn primary" type="submit">${state.evaluation.loading ? "评估中..." : "生成评估报告"}</button>
                    </div>
                </form>
            </section>
            <section class="section" style="margin-top: 18px;">
                ${renderEvaluationReport()}
            </section>
        `;
    }
    function renderEvaluationReport() {
        const report = state.evaluation.report;
        if (!report) {
            return `<div class="empty">暂无报告。选择时间范围后生成评估。</div>`;
        }
        return `
            <div class="grid three">
                ${statCard("Trace 总数", report.totalTraces, "本次纳入评估的请求数")}
                ${statCard("已标注", report.labeledTraces, "有人工标签的 Trace 数")}
                ${statCard("平均分", report.avgScore === null || report.avgScore === undefined ? "-" : Number(report.avgScore).toFixed(2), "综合评分")}
            </div>
            <div class="subtle-divider"></div>
            <div class="grid two">
                <div>
                    <h3>指标均值</h3>
                    ${renderMetrics(report.metricAverages)}
                </div>
                <div>
                    <h3>报告范围</h3>
                    <p class="muted">${escapeHtml(report.startAt)} 至 ${escapeHtml(report.endAt)}</p>
                </div>
            </div>
            <div class="subtle-divider"></div>
            ${renderEvaluationTable(report.traceResults || [])}
        `;
    }
    function renderMetrics(metrics) {
        const entries = Object.entries(metrics || {});
        if (!entries.length) {
            return `<div class="empty">暂无指标</div>`;
        }
        return `<div class="chips">${entries.map(([key, value]) => `<span class="chip selected">${escapeHtml(key)}：${Number(value).toFixed(2)}</span>`).join("")}</div>`;
    }
    function renderEvaluationTable(rows) {
        if (!rows.length) {
            return `<div class="empty">暂无 Trace 明细</div>`;
        }
        return `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Trace ID</th>
                            <th>会话</th>
                            <th>综合分</th>
                            <th>规则分</th>
                            <th>LLM 分</th>
                            <th>反馈分</th>
                            <th>指标 / 明细</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rows.map((row) => `
                            <tr>
                                <td>${escapeHtml(row.traceId)}</td>
                                <td>${escapeHtml(row.sessionId)}</td>
                                <td>${formatScore(row.score)}</td>
                                <td>${formatScore(row.ruleScore)}</td>
                                <td>${formatScore(row.llmJudgeScore)}</td>
                                <td>${formatScore(row.userFeedbackScore)}</td>
                                <td>
                                    <details>
                                        <summary>查看 JSON</summary>
                                        <pre class="json-box">${escapeHtml(JSON.stringify({ metrics: row.metrics, detail: row.detail }, null, 2))}</pre>
                                    </details>
                                </td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
    function formatScore(value) {
        return value === null || value === undefined ? "-" : Number(value).toFixed(2);
    }
    async function runEvaluation(form) {
        const formData = new FormData(form);
        state.evaluation.form = {
            startAt: formData.get("startAt"),
            endAt: formData.get("endAt"),
            limit: Number(formData.get("limit") || 50),
            includeLlmJudge: formData.get("includeLlmJudge") === "true"
        };
        state.evaluation.loading = true;
        renderEvaluations();
        try {
            state.evaluation.report = await DietApi.evaluate(state.evaluation.form);
        } catch (error) {
            showToast(error.message || "评估失败", "error");
        } finally {
            state.evaluation.loading = false;
            renderEvaluations();
        }
    }
    async function saveFeedback(button) {
        await guard(async () => {
            await DietApi.saveFeedback({
                sessionId: button.dataset.sessionId || state.chat.sessionId,
                itemId: Number(button.dataset.itemId),
                action: button.dataset.actionValue,
                rating: button.dataset.actionValue === "DISLIKE" ? 2 : 5,
                reason: ""
            });
        }, "反馈已记录");
    }
    function handleClick(event) {
        const target = event.target.closest("[data-action]");
        if (!target) {
            return;
        }
        const action = target.dataset.action;
        if (action === "set-source") {
            state.chat.sourceMode = target.dataset.source;
            resetChat();
        } else if (action === "new-session") {
            resetChat();
        } else if (action === "quick-message") {
            const input = document.querySelector("#chatForm textarea[name=message]");
            if (input) {
                input.value = target.dataset.message;
                input.focus();
            }
        } else if (action === "feedback") {
            saveFeedback(target);
        } else if (action === "new-meal") {
            state.editingMeal = emptyMeal();
            renderPersonalMeals();
        } else if (action === "edit-meal") {
            editMeal(target.dataset.id);
        } else if (action === "delete-meal") {
            deleteMeal(target.dataset.id);
        } else if (action === "cancel-edit") {
            state.editingMeal = null;
            renderPersonalMeals();
        } else if (action === "select-trace") {
            selectTrace(target.dataset.traceId);
        } else if (action === "open-trace") {
            state.traces.filters.sessionId = "";
            navigate("/admin/traces");
            selectTrace(target.dataset.traceId);
        } else if (action === "add-checkin-item") {
            addCheckinItem();
        } else if (action === "remove-checkin-item") {
            removeCheckinItem(target.dataset.index);
        } else if (action === "delete-checkin") {
            deleteCheckin(target.dataset.id);
        } else if (action === "view-checkin-image") {
            viewCheckinImage(target.dataset.id);
        }
    }
    function handleSubmit(event) {
        const form = event.target;
        if (form.id === "chatForm") {
            event.preventDefault();
            submitChat(form);
        } else if (form.id === "checkinImageForm") {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            recognizeCheckinImage(form);
        } else if (form.id === "checkinConfirmForm") {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            saveCheckin(form);
        } else if (form.id === "checkinDateForm") {
            event.preventDefault();
            changeCheckinDate(form);
        } else if (form.id === "profileForm") {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            saveProfile(form);
        } else if (form.id === "mealForm") {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            saveMeal(form);
        } else if (form.id === "traceFilterForm") {
            event.preventDefault();
            searchTraces(form);
        } else if (form.id === "traceLabelForm") {
            event.preventDefault();
            saveTraceLabel(form);
        } else if (form.id === "evaluationForm") {
            event.preventDefault();
            runEvaluation(form);
        }
    }
    function resetUserScopedState() {
        state.home = { loaded: false, personalCount: 0, publicCount: 0 };
        state.profile = { loaded: false, loading: false, data: null };
        revokeCheckinPreview();
        state.checkins = defaultCheckinState();
        state.personalMeals = [];
        state.publicMeals = [];
        state.editingMeal = null;
        state.slotOptions = null;
        state.traces.rows = [];
        state.traces.selected = null;
        state.traces.filters = defaultTraceFilters();
        state.evaluation.report = null;
        state.evaluation.form = defaultRangeForm();
        state.chat.sessionId = null;
        state.chat.sending = false;
        state.chat.messages = [{
            role: "assistant",
            text: "你好，我可以根据你的个人餐食库或公共餐食库推荐今天吃什么。可以试试问我：今晚想吃清淡一点，有什么推荐？"
        }];
    }
    function updateAuthUi() {
        const user = DietApi.getCurrentUser();
        authUserLabel.textContent = user ? (user.displayName || user.username) : "未登录";
        logoutButton.hidden = !user;
    }
    function setAuthMode(mode) {
        const registering = mode === "register";
        loginForm.hidden = registering;
        registerForm.hidden = !registering;
        authTitle.textContent = registering ? "创建你的账号" : "登录后开始使用";
    }
    function openAuthPanel(mode) {
        setAuthMode(mode || "login");
        authPanel.classList.add("is-open");
    }
    function closeAuthPanel() {
        authPanel.classList.remove("is-open");
    }
    async function submitAuthentication(event, mode) {
        event.preventDefault();
        const form = event.target;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const payload = Object.fromEntries(new FormData(form).entries());
        try {
            await (mode === "register" ? DietApi.register(payload) : DietApi.login(payload));
            form.reset();
            resetUserScopedState();
            updateAuthUi();
            closeAuthPanel();
            showToast(mode === "register" ? "注册成功，已自动登录" : "登录成功");
            if (!location.hash) {
                navigate("/diet");
            } else {
                render();
            }
        } catch (error) {
            showToast(error.message || "登录失败", "error");
        }
    }
    function initAuthentication() {
        loginForm.addEventListener("submit", (event) => submitAuthentication(event, "login"));
        registerForm.addEventListener("submit", (event) => submitAuthentication(event, "register"));
        document.getElementById("showRegisterButton").addEventListener("click", () => setAuthMode("register"));
        document.getElementById("showLoginButton").addEventListener("click", () => setAuthMode("login"));
        logoutButton.addEventListener("click", () => {
            DietApi.logout();
            resetUserScopedState();
            updateAuthUi();
            openAuthPanel("login");
            showToast("已退出登录");
        });
        window.addEventListener("diet:unauthorized", () => {
            resetUserScopedState();
            updateAuthUi();
            openAuthPanel("login");
        });
        updateAuthUi();
        if (!DietApi.isAuthenticated()) {
            openAuthPanel("login");
        }
    }
    window.addEventListener("hashchange", render);
    app.addEventListener("click", handleClick);
    app.addEventListener("submit", handleSubmit);
    initAuthentication();
    if (!location.hash && DietApi.isAuthenticated()) {
        navigate("/diet");
    } else if (DietApi.isAuthenticated()) {
        render();
    }
})();

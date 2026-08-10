(function () {
    "use strict";

    const API_BASE = "/api/v1/diet";
    const AUTH_BASE = "/api/v1/auth";
    const TOKEN_KEY = "diet.accessToken";
    const USER_KEY = "diet.authUser";

    function getToken() {
        return localStorage.getItem(TOKEN_KEY) || "";
    }

    function getCurrentUser() {
        const raw = localStorage.getItem(USER_KEY);
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch (error) {
            logout();
            return null;
        }
    }

    function saveAuth(payload) {
        localStorage.setItem(TOKEN_KEY, payload.accessToken);
        localStorage.setItem(USER_KEY, JSON.stringify(payload.user));
        return payload;
    }

    function logout() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }

    async function request(path, options) {
        const config = options || {};
        const headers = new Headers(config.headers || {});
        const token = getToken();
        if (token) {
            headers.set("Authorization", `Bearer ${token}`);
        }

        if (config.body !== undefined && !(config.body instanceof FormData)) {
            headers.set("Content-Type", "application/json");
        }

        const response = await fetch(`${API_BASE}${path}`, {
            ...config,
            headers,
            body: config.body === undefined || config.body instanceof FormData
                ? config.body
                : JSON.stringify(config.body)
        });

        if (!response.ok) {
            const detail = await readError(response);
            if (response.status === 401) {
                logout();
                window.dispatchEvent(new CustomEvent("diet:unauthorized"));
            }
            throw new Error(detail || `请求失败：${response.status}`);
        }

        if (response.status === 204) {
            return null;
        }

        const text = await response.text();
        if (!text) {
            return null;
        }

        try {
            return JSON.parse(text);
        } catch (error) {
            return text;
        }
    }

    async function authRequest(path, payload) {
        const response = await fetch(`${AUTH_BASE}${path}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            throw new Error((await readError(response)) || `请求失败：${response.status}`);
        }
        return saveAuth(await response.json());
    }

    async function readError(response) {
        const text = await response.text();
        if (!text) {
            return "";
        }

        try {
            const payload = JSON.parse(text);
            return payload.message || payload.error || text;
        } catch (error) {
            return text;
        }
    }

    function toQuery(params) {
        const search = new URLSearchParams();
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== "") {
                search.set(key, value);
            }
        });
        const query = search.toString();
        return query ? `?${query}` : "";
    }

    window.DietApi = {
        getCurrentUser,
        isAuthenticated: () => Boolean(getToken() && getCurrentUser()),
        register: (payload) => authRequest("/register", payload),
        login: (payload) => authRequest("/login", payload),
        logout,
        getProfile: () => request("/profile"),
        updateProfile: (payload) => request("/profile", { method: "PUT", body: payload }),
        createSession: () => request("/sessions", { method: "POST" }),
        chat: (payload) => request("/chat", { method: "POST", body: payload }),
        listPersonalMeals: () => request("/meals/personal"),
        createPersonalMeal: (payload) => request("/meals/personal", { method: "POST", body: payload }),
        updatePersonalMeal: (mealId, payload) => request(`/meals/personal/${encodeURIComponent(mealId)}`, { method: "PUT", body: payload }),
        deletePersonalMeal: (mealId) => request(`/meals/personal/${encodeURIComponent(mealId)}`, { method: "DELETE" }),
        listPublicMeals: () => request("/meals/public"),
        slotOptions: () => request("/slot-options"),
        saveFeedback: (payload) => request("/feedback", { method: "POST", body: payload }),
        listTraces: (params) => request(`/debug/traces${toQuery(params)}`),
        getTrace: (traceId) => request(`/debug/traces/${encodeURIComponent(traceId)}`),
        listSessionTraces: (sessionId, limit) => request(`/debug/sessions/${encodeURIComponent(sessionId)}/traces${toQuery({ limit })}`),
        labelTrace: (traceId, payload) => request(`/debug/traces/${encodeURIComponent(traceId)}/label`, { method: "PUT", body: payload }),
        evaluate: (payload) => request("/evaluations", { method: "POST", body: payload })
    };
})();



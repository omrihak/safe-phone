// Blocked-page controller. The dynamic redirect rule appends ?host=...&pattern=... .
// We render those, ask the service worker for the live policy/break status,
// and let the user start a local break that mirrors BreakManager semantics.

const params = new URLSearchParams(location.search);
const host = params.get("host") ?? "";
const pattern = params.get("pattern") ?? "";

document.getElementById("host").textContent = host || "(unknown host)";
document.getElementById("pattern").textContent = pattern || host || "(none)";

const breakBtn = document.getElementById("break-btn");
const backBtn = document.getElementById("back-btn");
const statusEl = document.getElementById("status");
const profileEl = document.getElementById("profile");
const remainingEl = document.getElementById("remaining");
const nextGrantEl = document.getElementById("next-grant");

backBtn.addEventListener("click", () => {
  if (history.length > 1) history.back();
  else location.href = "https://www.google.com";
});

breakBtn.addEventListener("click", async () => {
  setStatus("Starting break…", false);
  breakBtn.disabled = true;
  const res = await sendMessage({ type: "startBreak" });
  if (!res?.ok || !res.result?.ok) {
    setStatus(res?.result?.message || res?.message || "Could not start break", true);
    await refresh();
    return;
  }
  setStatus(`Break started — reloading ${host || "page"}…`, false);
  // Brief pause so the user sees the confirmation, then retry the original
  // URL. The dynamic rules have already been cleared by the service worker.
  setTimeout(() => {
    if (host) location.replace(`https://${host}`);
    else history.back();
  }, 600);
});

async function refresh() {
  const res = await sendMessage({ type: "getStatus" });
  if (!res?.ok) {
    setStatus(res?.message ?? "Could not load status", true);
    return;
  }
  const s = res.result;
  profileEl.textContent = s.decision.activeProfileName ?? "—";
  remainingEl.textContent = String(s.breaksRemainingToday);
  if (s.minutesUntilNextBreak == null && s.breaksRemainingToday === 0) {
    nextGrantEl.hidden = false;
    nextGrantEl.textContent = "All daily breaks used";
  } else if (s.breaksRemainingToday === 0 && s.minutesUntilNextBreak != null) {
    nextGrantEl.hidden = false;
    nextGrantEl.textContent = `Next break unlocks in ~${s.minutesUntilNextBreak} min`;
  } else {
    nextGrantEl.hidden = true;
  }
  const onBreak = s.decision.onBreak;
  if (onBreak) {
    breakBtn.disabled = true;
    breakBtn.textContent = "On break";
  } else if (s.breaksRemainingToday > 0) {
    breakBtn.disabled = false;
    const mins = s.breakDurationMinutes || 0;
    breakBtn.textContent = mins > 0 ? `Take a ${mins}-minute break` : "Take a break";
  } else {
    breakBtn.disabled = true;
    breakBtn.textContent = "No breaks remaining";
  }
}

function setStatus(text, isError) {
  statusEl.textContent = text;
  statusEl.classList.toggle("error", !!isError);
}

function sendMessage(msg) {
  return new Promise((resolve) => {
    try {
      chrome.runtime.sendMessage(msg, (res) => {
        if (chrome.runtime.lastError) {
          resolve({ ok: false, message: chrome.runtime.lastError.message });
        } else {
          resolve(res);
        }
      });
    } catch (e) {
      resolve({ ok: false, message: e?.message ?? String(e) });
    }
  });
}

refresh();

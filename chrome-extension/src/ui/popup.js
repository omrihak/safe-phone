// Popup controller. Reads live status from the service worker, and exposes
// "pull now" + the same start/end break controls the blocked page uses.

const pill = document.getElementById("state-pill");
const profileEl = document.getElementById("profile");
const rulesCountEl = document.getElementById("rules-count");
const remainingEl = document.getElementById("remaining");
const nextGrantRow = document.getElementById("next-grant-row");
const nextGrantEl = document.getElementById("next-grant");
const lastSyncEl = document.getElementById("last-sync");
const breakBtn = document.getElementById("break-btn");
const endBreakBtn = document.getElementById("end-break-btn");
const pullBtn = document.getElementById("pull-btn");
const statusEl = document.getElementById("status");

pullBtn.addEventListener("click", async () => {
  setStatus("Pulling…", false);
  pullBtn.disabled = true;
  const res = await sendMessage({ type: "pullNow" });
  pullBtn.disabled = false;
  if (!res?.ok) {
    setStatus(res?.message ?? "Pull failed", true);
  } else if (res.result?.ok === false) {
    setStatus(res.result.message ?? "Pull failed", true);
  } else if (res.result?.changed === false) {
    setStatus("Already up to date", false);
  } else {
    setStatus("Pulled snapshot", false);
  }
  await refresh();
});

breakBtn.addEventListener("click", async () => {
  setStatus("Starting break…", false);
  breakBtn.disabled = true;
  const res = await sendMessage({ type: "startBreak" });
  if (!res?.ok || !res.result?.ok) {
    setStatus(res?.result?.message ?? res?.message ?? "Could not start break", true);
  } else {
    setStatus("Break started", false);
  }
  await refresh();
});

endBreakBtn.addEventListener("click", async () => {
  setStatus("Ending break…", false);
  endBreakBtn.disabled = true;
  const res = await sendMessage({ type: "endBreak" });
  if (!res?.ok || !res.result?.ok) {
    setStatus(res?.result?.message ?? res?.message ?? "Could not end break", true);
  } else {
    setStatus("Break ended", false);
  }
  await refresh();
});

async function refresh() {
  const res = await sendMessage({ type: "getStatus" });
  if (!res?.ok) {
    setStatus(res?.message ?? "Could not load status", true);
    return;
  }
  const s = res.result;
  profileEl.textContent = s.decision.activeProfileName ?? "—";
  rulesCountEl.textContent = s.snapshotLoaded ? String(s.blockedPatternsCount) : "policy not loaded";
  remainingEl.textContent = String(s.breaksRemainingToday);
  lastSyncEl.textContent = formatLastSync(s.lastSync);

  if (s.decision.onBreak) {
    pill.textContent = "On break";
    pill.className = "status-pill onbreak";
    breakBtn.hidden = true;
    endBreakBtn.hidden = false;
    endBreakBtn.disabled = false;
  } else if (s.decision.enforcing) {
    pill.textContent = "Enforcing";
    pill.className = "status-pill enforcing";
    breakBtn.hidden = false;
    endBreakBtn.hidden = true;
    breakBtn.disabled = s.breaksRemainingToday === 0;
    const mins = s.breakDurationMinutes || 0;
    breakBtn.textContent =
      s.breaksRemainingToday === 0
        ? "No breaks remaining"
        : mins > 0
          ? `Take a ${mins}-minute break`
          : "Take a break";
  } else {
    pill.textContent = s.decision.reason || "Idle";
    pill.className = "status-pill idle";
    breakBtn.hidden = false;
    endBreakBtn.hidden = true;
    // Allow starting a break only when there *would* be enforcement otherwise;
    // when off-day or not configured, breaks are meaningless so we hide.
    breakBtn.disabled = true;
    breakBtn.textContent = "Take a break";
  }

  if (s.breaksRemainingToday === 0 && s.minutesUntilNextBreak != null) {
    nextGrantRow.hidden = false;
    nextGrantEl.textContent = `~${s.minutesUntilNextBreak} min`;
  } else {
    nextGrantRow.hidden = true;
  }
}

function formatLastSync(s) {
  if (!s) return "never";
  if (s.inProgress) return "in progress…";
  if (!s.atMs) return s.message ?? "—";
  const ago = Date.now() - s.atMs;
  const mins = Math.floor(ago / 60_000);
  const secs = Math.floor((ago % 60_000) / 1000);
  const when = mins > 0 ? `${mins}m ago` : `${secs}s ago`;
  if (s.ok === false) return `failed ${when}`;
  return when;
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

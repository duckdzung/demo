import http from "k6/http";
import { check } from "k6";

export let options = {
  vus: 150,
  duration: "5m",
  rps: 300,
};

export default function () {
  let res = http.get("http://localhost:30080/users");

  check(res, {
    "status is 200": (r) => r.status === 200,
  });
}

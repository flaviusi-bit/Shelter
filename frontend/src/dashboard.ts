export type DashboardItem={id:string;animalId:string;animalName:string;medication:string;dose:string;route:string;scheduledAt:string;administeredAt?:string;administeredBy?:string;status:string}
export type DashboardTask={id:string;animalId?:string;animalName?:string;taskType:string;title:string;dueAt:string;status:string;priority:string;assignedTo?:string;notes?:string}
export type Dashboard={items:DashboardItem[];overdue:number;remaining:number;administered:number;overdueTasks:number;remainingTasks:number;taskItems:DashboardTask[]}
async function dashboardError(r:Response):Promise<Error>{
 const body=await r.text()
 if(body){try{const parsed=JSON.parse(body) as {message?:string};if(parsed.message)return new Error(parsed.message)}catch{}}
 return new Error('Request failed ('+r.status+')')
}
export async function getDashboard():Promise<Dashboard>{
 const auth=localStorage.getItem('shelterAuth')
 const r=await fetch('/api/dashboard/today',{headers:auth?{Authorization:'Basic '+auth}:{}})
 if(r.status===401){localStorage.removeItem('shelterAuth');window.dispatchEvent(new Event('shelter-auth-required'));throw new Error('Authentication required')}
 if(!r.ok)throw await dashboardError(r)
 return r.json()
}
